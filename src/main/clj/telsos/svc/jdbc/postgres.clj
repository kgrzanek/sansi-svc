(ns telsos.svc.jdbc.postgres
  (:require
   [clojure.java.jdbc :as java-jdbc]
   [jsonista.core :as json]
   [telsos.lib.assertions :refer [opt the]]
   [telsos.svc.jdbc :as jdbc]
   [tick.core :as tick])
  (:import
   (org.postgresql.util PGobject PSQLException)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; SERIALIZABLE FAILURES DETECTION AND RESTARTS
(defn- serialization-failure?
  [obj]
  (or (and (instance? PSQLException obj)
           ;; https://www.postgresql.org/docs/14/errcodes-appendix.html
           (= "40001" (.getSQLState ^PSQLException obj)))

      (and (instance? Exception obj)
           (recur (.getCause ^Exception obj)))))

(defn- signal-restarting-event*
  [events-handler event]
  (the map? event)
  (-> event
      (assoc :thread-id (.threadId (Thread/currentThread)) :instant (tick/instant))
      events-handler))

(defmacro ^:private signal-restarting-event
  [events-handler event]
  (the symbol? events-handler)
  `(when ~events-handler
     (signal-restarting-event* ~events-handler ~event)))

(defn restarting-on-serialization-failures*
  [times perfstats restarts-counter events-handler body]
  (the nat-int?                          times)
  (opt jdbc/perfstats?               perfstats)
  (opt jdbc/restarts-counter? restarts-counter)
  (opt ifn?                     events-handler)
  (the ifn?                               body)

  (let [start-nanos (when perfstats (System/nanoTime))

        result
        (loop [i 0]
          (if (= i times)
            ;; The last attempt - no special treatment, just evaluation of body
            (do (signal-restarting-event events-handler {:last-attempt i})
                (body))

            (let [result
                  (try
                    (signal-restarting-event events-handler {:attempt i})
                    (body)

                    (catch Exception e
                      (when-not (serialization-failure? e)
                        ;; Other exceptions are simply re-thrown
                        (signal-restarting-event events-handler {:no-pg-serialization-failure e})
                        (throw e))

                      (when restarts-counter
                        (let [cnt (jdbc/restarts-counter-inc! restarts-counter)]
                          (signal-restarting-event events-handler {:restarts-counter-inc! cnt})))

                      ::serialization-failure))]

              (if (not= ::serialization-failure result)
                (do (signal-restarting-event events-handler {:exit-loop-with result})
                    result)

                (recur (unchecked-inc-int i))))))]

    (when perfstats
      (let [end-nanos (System/nanoTime)]
        (signal-restarting-event events-handler {:perfstats-update! [start-nanos end-nanos]})
        (jdbc/perfstats-update! perfstats start-nanos end-nanos)))

    (signal-restarting-event events-handler {:return result})
    result))

(defmacro restarting
  [{:keys [times perfstats restarts-counter events-handler]} & body]
  `(restarting-on-serialization-failures*
     ~times ~perfstats ~restarts-counter ~events-handler
     (fn [] ~@body)))

;; JSONB
(defn create-jsonb-object [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/write-value-as-string value json/keyword-keys-object-mapper))))

(extend-protocol java-jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [m] (create-jsonb-object m))

  clojure.lang.IPersistentVector
  (sql-value [v] (create-jsonb-object v))

  clojure.lang.Keyword
  (sql-value [k] (name k))

  java.util.UUID
  (sql-value [uuid] (str uuid)))

(extend-protocol java-jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _ _]
    (.toInstant v))

  PGobject
  (result-set-read-column [pgobj _ _]
    (let [value (.getValue pgobj)]
      (case (.getType pgobj)
        "jsonb" (json/read-value value json/keyword-keys-object-mapper)
        :else   value))))
