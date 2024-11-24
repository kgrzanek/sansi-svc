(ns telsos.svc.jdbc
  (:require
   [clojure.string :as str]
   [hikari-cp.core :as hikari]
   [hugsql.adapter :as hugsql-adapter]
   [hugsql.adapter.next-jdbc :as next-adapter]
   [hugsql.core :as hugsql]
   [next.jdbc :as next]
   [next.jdbc.protocols :as next-protocols]
   [next.jdbc.result-set :as next-result-set]
   [telsos.lib.assertions :refer [opt the]]
   [telsos.lib.binding :as binding]
   [telsos.lib.fast :as fast]
   [telsos.lib.logging :as log])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.util.concurrent.atomic AtomicLong)
   (telsos.lib PerfStats)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(->> {:builder-fn next-result-set/as-unqualified-maps}
     (next-adapter/hugsql-adapter-next-jdbc)
     (hugsql/set-adapter!))

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
  (or (connection? x) ;; ~1ns
      (datasource? x) ;; ~1ns

      ;; ~5μs - we should avoid getting here if possible
      (satisfies? next-protocols/Sourceable x)))

(def t-conn* (binding/create-scoped))

(defn in-transaction? [] (some?        @t-conn*))
(defn no-transaction? [] (not (in-transaction?)))

(defmacro no-transaction!
  [& body]
  `(if (in-transaction?)
     (throw (IllegalStateException. "body executed in transaction"))
     (do ~@body)))

(defn tx*
  [transactable isolation read-only? rollback-only? body]
  (the transactable? transactable)
  (opt isolation-value? isolation)
  (next/with-transaction
    [t-conn transactable {:isolation     (or isolation :read-committed)
                          :read-only     read-only?
                          :rollback-only rollback-only?}]

    (binding/scoped [t-conn* t-conn] (body))))

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

;; SERIALIZABLE RESTARTS AND PERFORMANCE STATISTICS
(defn create-perfstats
  ([n]
   (PerfStats. n))

  ([n & ns-]
   (vec (cons (create-perfstats n) (map create-perfstats ns-)))))

(defn perfstats?
  [perfstats]
  (or (instance? PerfStats perfstats)
      (and (vector?        perfstats)
           (fast/vec-every? #(instance? PerfStats %) perfstats))))

(defn perfstats-update!
  [perfstats ^long start-nanos ^long end-nanos]
  (the perfstats? perfstats)
  (if (instance? PerfStats perfstats)
    (.update ^PerfStats perfstats start-nanos end-nanos)

    (dotimes [i (count perfstats)]
      (perfstats-update! (nth perfstats i) start-nanos end-nanos)))

  perfstats)

(defn perfstats-msecs
  [perfstats]
  (the perfstats? perfstats)
  (if (instance? PerfStats perfstats)
    (let [[n acc avg] (.statsMsecs ^PerfStats perfstats)]
      {:n (long n) :acc-msecs acc :avg-msecs avg})

    (mapv perfstats-msecs perfstats)))

(defn create-restarts-counter ^AtomicLong
  []
  (AtomicLong.))

(defn restarts-counter?
  [rc]
  (instance? AtomicLong rc))

(defn restarts-counter-inc! ^long
  [^AtomicLong restarts-counter]
  (.incrementAndGet restarts-counter))

(defn restarts-count ^long
  [^AtomicLong restarts-counter]
  (.get restarts-counter))

;; HUGSQL LOGGING
(def log-hugsql*
  (-> "telsos.jdbc.log-hugsql"
      (System/getProperty #_default "false")
      (Boolean/parseBoolean)
      (binding/create-scoped)))

(defmacro logging-hugsql
  [& body]
  `(binding-scoped [log-hugsql* true]
                   ~@body))

(defmacro no-logging-hugsql
  [& body]
  `(binding-scoped [log-hugsql* false]
                   ~@body))

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
    (hugsql-adapter/execute this db sqlvec options)

    #{:<! :returning-execute :? :query :default}
    (hugsql-adapter/query this db sqlvec options)))

;; hugsql-adapter/execute
(defmethod hugsql/hugsql-command-fn :!                 [_] `hugsql-logging-command-fn)
(defmethod hugsql/hugsql-command-fn :execute           [_] `hugsql-logging-command-fn)
(defmethod hugsql/hugsql-command-fn :i!                [_] `hugsql-logging-command-fn)
(defmethod hugsql/hugsql-command-fn :insert            [_] `hugsql-logging-command-fn)
;; hugsql-adapter/query
(defmethod hugsql/hugsql-command-fn :<!                [_] `hugsql-logging-command-fn)
(defmethod hugsql/hugsql-command-fn :returning-execute [_] `hugsql-logging-command-fn)
(defmethod hugsql/hugsql-command-fn :?                 [_] `hugsql-logging-command-fn)
(defmethod hugsql/hugsql-command-fn :query             [_] `hugsql-logging-command-fn)
(defmethod hugsql/hugsql-command-fn :default           [_] `hugsql-logging-command-fn)

;; HIKARI CREATION
(defn create-hikari-datasource
  [datasource-name config]
  (->> config (merge {:pool-name (name datasource-name)

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

       hikari/make-datasource))

(defn close-hikari!
  [datasource]
  (when-let [datasource (if (delay? datasource)
                          (when (realized? datasource) @datasource)
                          datasource)]
    (.close ^HikariDataSource datasource)))
