(ns telsos.svc.jdbc
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [hikari-cp.core :as hikari]
   [hugsql.adapter :as hugsql-adapter]
   [hugsql.adapter.next-jdbc :as next-adapter]
   [hugsql.core :as hugsql]
   [jsonista.core :as json]
   [next.jdbc :as next]
   [next.jdbc.result-set :as next-rs]
   [telsos.lib.binding :as binding]
   [telsos.lib.fast :as fast]
   [telsos.svc.logging :as log]
   [tick.core :as tick])
  (:import
   (java.util.concurrent.atomic AtomicLong)
   (telsos.lib PerfStats)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(->> {:builder-fn next-rs/as-unqualified-maps}
     (next-adapter/hugsql-adapter-next-jdbc)
     (hugsql/set-adapter!))

;; TRANSACTIONS
(def isolation-value?
  #{:none
    :read-committed
    :read-uncommitted
    :repeatable-read
    :serializable})

(def t-conn* (binding/create-scoped))

(defn in-transaction? [] (some?        @t-conn*))
(defn no-transaction? [] (not (in-transaction?)))

(defmacro no-transaction!
  [& body]
  `(if (in-transaction?)
     (throw (IllegalStateException. "body executed in transaction"))
     (do ~@body)))

(defn tx*
  [datasource isolation read-only? rollback-only? body]
  (assert datasource)
  (assert (or (nil? isolation) (isolation-value? isolation)))
  (next/with-transaction
    [t-conn

     #_transactable
     datasource

     #_opts
     {:isolation     (or isolation :read-committed)
      :read-only     read-only?
      :rollback-only rollback-only?}]

    (binding/scoped [t-conn* t-conn] (body))))

(defmacro read-committed
  [[datasource read-only? rollback-only?] & body]
  `(tx* ~datasource :read-committed ~read-only? ~rollback-only?
        (fn [& _] ~@body)))

(defmacro repeatable-read
  [[datasource read-only? rollback-only?] & body]
  `(tx* ~datasource :repeatable-read ~read-only? ~rollback-only?
        (fn [& _] ~@body)))

(defmacro serializable
  [[datasource read-only? rollback-only?] & body]
  `(tx* ~datasource :serializable ~read-only? ~rollback-only?
        (fn [& _] ~@body)))

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
  (assert (perfstats? perfstats))
  (if (instance? PerfStats perfstats)
    (.update ^PerfStats perfstats start-nanos end-nanos)

    (dotimes [i (count perfstats)]
      (perfstats-update! (nth perfstats i) start-nanos end-nanos)))

  perfstats)

(defn perfstats-msecs
  [perfstats]
  (assert (perfstats? perfstats))
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

;; SERIALIZABLE FAILURES DETECTION AND RESTARTS
(defn- pg-serialization-failure?
  [obj]
  (or (and (instance? org.postgresql.util.PSQLException obj)
           ;; https://www.postgresql.org/docs/14/errcodes-appendix.html
           (= "40001" (.getSQLState ^org.postgresql.util.PSQLException obj)))

      (and (instance? Exception obj)
           (recur (.getCause ^Exception obj)))))

(defn- signal-pg-restarting-event*
  [events-handler event]
  (assert (map? event))
  (-> event
      (assoc :thread-id (.threadId (Thread/currentThread)) :instant (tick/instant))
      events-handler))

(defmacro ^:private signal-pg-restarting-event
  [events-handler event]
  (assert (symbol? events-handler))
  `(when ~events-handler
     (signal-pg-restarting-event* ~events-handler ~event)))

(defn pg-restarting-on-serialization-failures*
  [times perfstats restarts-counter events-handler body]
  (assert (nat-int? times))
  (assert (or (nil?        perfstats) (perfstats?               perfstats)))
  (assert (or (nil? restarts-counter) (restarts-counter? restarts-counter)))
  (assert (or (nil?   events-handler) (ifn?                events-handler)))
  (assert (ifn? body))

  (let [start-nanos (when perfstats (System/nanoTime))

        result
        (loop [i 0]
          (if (= i times)
            ;; The last attempt - no special treatment, just evaluation of body
            (do (signal-pg-restarting-event events-handler {:last-attempt i})
                (body))

            (let [result
                  (try
                    (signal-pg-restarting-event events-handler {:attempt i})
                    (body)

                    (catch Exception e
                      (when-not (pg-serialization-failure? e)
                        ;; Other exceptions are simply re-thrown
                        (signal-pg-restarting-event events-handler {:no-pg-serialization-failure e})
                        (throw e))

                      (when restarts-counter
                        (let [cnt (restarts-counter-inc! restarts-counter)]
                          (signal-pg-restarting-event events-handler {:restarts-counter-inc! cnt})))

                      ::pg-serialization-failure))]

              (if (not= ::pg-serialization-failure result)
                (do (signal-pg-restarting-event events-handler {:exit-loop-with result})
                    result)

                (recur (unchecked-inc-int i))))))]

    (when perfstats
      (let [end-nanos (System/nanoTime)]
        (signal-pg-restarting-event events-handler {:perfstats-update! [start-nanos end-nanos]})
        (perfstats-update! perfstats start-nanos end-nanos)))

    (signal-pg-restarting-event events-handler {:return result})
    result))

(defmacro pg-restarting
  [{:keys [times perfstats restarts-counter events-handler]} & body]
  `(pg-restarting-on-serialization-failures*
     ~times ~perfstats ~restarts-counter ~events-handler
     (fn [] ~@body)))

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

;; JSONB
(defn create-jsonb-pg-object [value]
  (doto (org.postgresql.util.PGobject.)
    (.setType "jsonb")
    (.setValue (json/write-value-as-string value json/keyword-keys-object-mapper))))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [m] (create-jsonb-pg-object m))

  clojure.lang.IPersistentVector
  (sql-value [v] (create-jsonb-pg-object v))

  clojure.lang.Keyword
  (sql-value [k] (name k))

  java.util.UUID
  (sql-value [uuid] (str uuid)))

(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _ _]
    (.toInstant v))

  org.postgresql.util.PGobject
  (result-set-read-column [pgobj _ _]
    (let [value (.getValue pgobj)]
      (case (.getType pgobj)
        "jsonb" (json/read-value value json/keyword-keys-object-mapper)
        :else   value))))

;; HIKARI CREATION
(defn create-hikari-datasource
  [datasource-name config]
  (->> config (merge {:pool-name (str datasource-name)

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
