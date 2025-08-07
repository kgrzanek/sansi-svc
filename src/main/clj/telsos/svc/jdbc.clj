(ns telsos.svc.jdbc
  (:require
   [clojure.string :as str]
   [hikari-cp.core]
   [hugsql.adapter]
   [hugsql.adapter.next-jdbc]
   [hugsql.core]
   [next.jdbc]
   [next.jdbc.protocols]
   [next.jdbc.result-set]
   [telsos.lib.assertions :refer [maybe the]]
   [telsos.lib.binding]
   [telsos.lib.logging :as log]
   [tick.core :as t])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(->> {:builder-fn next.jdbc.result-set/as-unqualified-maps}
     (hugsql.adapter.next-jdbc/hugsql-adapter-next-jdbc)
     (hugsql.core/set-adapter!))

;; TRANSACTIONS
(def isolation-value?
  #{:none
    :read-committed
    :read-uncommitted
    :repeatable-read
    :serializable})

(defn connection? [x] (instance? java.sql.Connection  x))
(defn datasource? [x] (instance? javax.sql.DataSource x))

(defn transactable? [x] ;; as understood in next.jdbc
  (or (connection?   x) ;; ~1ns
      (datasource?   x) ;; ~1ns

      ;; ~5μs - we should avoid getting here if possible
      (satisfies? next.jdbc.protocols/Sourceable x)))

(def t-conn* (telsos.lib.binding/create-scoped))

(defn in-transaction? [] (some?       @t-conn*))
(defn no-transaction? [] (not (in-transaction?)))

(defmacro no-transaction!
  [& body]
  `(if (in-transaction?)
     (throw (IllegalStateException. "body executed in transaction"))
     (do ~@body)))

(defn tx*
  [transactable isolation read-only? rollback-only? body]
  (the   transactable? transactable)
  (maybe isolation-value? isolation)
  (next.jdbc/with-transaction
    [t-conn transactable {:isolation     (or isolation :read-committed)
                          :read-only     read-only?
                          :rollback-only rollback-only?}]

    (telsos.lib.binding/scoped [t-conn* t-conn] (body))))

(defmacro read-committed
  [[transactable read-only? rollback-only?] & body]
  `(tx* ~transactable :read-committed ~read-only? ~rollback-only?
        (fn [& _more#] ~@body)))

(defmacro repeatable-read
  [[transactable read-only? rollback-only?] & body]
  `(tx* ~transactable :repeatable-read ~read-only? ~rollback-only?
        (fn [& _more#] ~@body)))

(defmacro serializable
  [[transactable read-only? rollback-only?] & body]
  `(tx* ~transactable :serializable ~read-only? ~rollback-only?
        (fn [& _more#] ~@body)))

;; SERIALIZATION FAILURES, RESTARTS, AND PERFORMANCE STATISTICS
(defn- -serialization-failure? [pred ^Throwable e]
  (when (some? e)
    (or (pred e)
        (-serialization-failure? pred (.getCause e))
        (loop [supps (seq (.getSuppressed e))]
          (when supps
            (or (-serialization-failure? pred (first supps))
                (recur (next supps))))))))

(defonce ^:private SERIALIZATION-FAILURE (Object.))

(defmacro ^:private send-event
  [events-handler k v & kvs]
  (the symbol? events-handler)
  (let [event (apply hash-map k v kvs)
        event (assoc event
                     :thread-id `(.threadId (Thread/currentThread))
                     :instant   `(t/instant))]

    `(when (ifn? ~events-handler)
       (~events-handler ~event))))

(defn restarting-on-serialization-failures*
  [{:keys [^long times
           serialization-failure?
           restarts-counter-atom
           events-handler]} body]

  (when-not (nat-int? times)
    (throw (ex-info "times must be >= 0" {:times times})))

  (let [result
        (loop [i 0]
          (if (= i times)
            (do (send-event events-handler :attempt-last i)
                ;; The last attempt - no special treatment, just evaluation of body:
                (body))

            (let [result
                  (try
                    (send-event events-handler :attempt i)
                    (body)

                    (catch Exception e
                      (when-not (-serialization-failure? serialization-failure? e)
                        (send-event events-handler :exception e)
                        ;; Exceptions other than serialization failures are simply re-thrown:
                        (throw e))

                      ;; otherwise, on serialization failure:
                      (when restarts-counter-atom
                        (let [cnt (swap! restarts-counter-atom inc)]
                          (send-event events-handler :restarts-count cnt)))

                      SERIALIZATION-FAILURE))]

              (if-not (identical? SERIALIZATION-FAILURE result)
                result
                (recur (inc i))))))]

    (send-event events-handler :result result)
    result))

(defmacro restarting
  [options & body]
  `(restarting-on-serialization-failures* ~options (fn [] ~@body)))

;; HUGSQL LOGGING
(def log-hugsql*
  (-> "telsos.jdbc.log-hugsql"
      (System/getProperty #_default "false")
      Boolean/parseBoolean
      telsos.lib.binding/create-scoped))

(defmacro logging-hugsql
  [& body]
  `(binding-scoped [log-hugsql* true] ~@body))

(defmacro no-logging-hugsql
  [& body]
  `(binding-scoped [log-hugsql* false] ~@body))

(defn- sqlvec->string [sqlvec]
  (->> sqlvec
       (map #(str/replace (str %) #"\n" " "))
       (str/join "; ")
       (str/trim)))

(defn- hugsql-logging-command-fn
  [this db sqlvec options]
  (when @log-hugsql*
    ;; It's a diagnostic functionality for dev only, that's why we stick to DEBUG
    (log/debug (str "HugSQL: " (sqlvec->string sqlvec))))

  (condp contains? (:command options)
    #{:! :execute :i! :insert}
    (hugsql.adapter/execute this db sqlvec options)

    #{:<! :returning-execute :? :query :default}
    (hugsql.adapter/query this db sqlvec options)))

;; hugsql-adapter/execute
(defmethod hugsql.core/hugsql-command-fn :!                 [_] `hugsql-logging-command-fn)
(defmethod hugsql.core/hugsql-command-fn :execute           [_] `hugsql-logging-command-fn)
(defmethod hugsql.core/hugsql-command-fn :i!                [_] `hugsql-logging-command-fn)
(defmethod hugsql.core/hugsql-command-fn :insert            [_] `hugsql-logging-command-fn)
;; hugsql-adapter/query
(defmethod hugsql.core/hugsql-command-fn :<!                [_] `hugsql-logging-command-fn)
(defmethod hugsql.core/hugsql-command-fn :returning-execute [_] `hugsql-logging-command-fn)
(defmethod hugsql.core/hugsql-command-fn :?                 [_] `hugsql-logging-command-fn)
(defmethod hugsql.core/hugsql-command-fn :query             [_] `hugsql-logging-command-fn)
(defmethod hugsql.core/hugsql-command-fn :default           [_] `hugsql-logging-command-fn)

;; HIKARI CREATION
(defn create-hikari-datasource
  [datasource-name config]
  (->> config
       (merge {:pool-name (name datasource-name)

               ;; When auto-commit is false, the lack of explicit commit
               ;; rolls the transaction back on the return to the pool;
               ;; we keep the default true
               :auto-commit true

               :connection-timeout 30000
               :idle-timeout       600000
               :max-lifetime       1800000
               :maximum-pool-size  10
               :minimum-idle       10
               :validation-timeout 5000
               :read-only          false
               :register-mbeans    false})

       ;; :ssl            true
       ;; :ssl-mode      "require"

       hikari-cp.core/make-datasource))

(defn close-hikari!
  [datasource]
  (when-let [datasource (if (delay? datasource)
                          (when (realized? datasource) @datasource)
                          datasource)]
    (.close ^HikariDataSource datasource)))
