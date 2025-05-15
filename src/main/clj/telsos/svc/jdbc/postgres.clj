(ns telsos.svc.jdbc.postgres
  (:require
   [clojure.java.jdbc]
   [telsos.lib.edn-json])
  (:import
   (org.postgresql.util PGobject PSQLException)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; SERIALIZABLE FAILURES DETECTION AND RESTARTS
(defn serialization-failure?
  [obj]
  (and (instance? PSQLException obj)
       (= "40001" (.getSQLState ^PSQLException obj))))

;; JSONB
(defn create-jsonb-object [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (telsos.lib.edn-json/edn->json-string value))))

(extend-protocol clojure.java.jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [m] (create-jsonb-object m))

  clojure.lang.IPersistentVector
  (sql-value [v] (create-jsonb-object v))

  clojure.lang.Keyword
  (sql-value [k] (name k))

  java.util.UUID
  (sql-value [uuid] (str uuid)))

(extend-protocol clojure.java.jdbc/IResultSetReadColumn
  java.sql.Timestamp
  (result-set-read-column [v _ _]
    (.toInstant v))

  PGobject
  (result-set-read-column [pgobj _ _]
    (let [value (.getValue pgobj)]
      (case (.getType pgobj)
        "jsonb" (telsos.lib.edn-json/json-string->edn (str value))
        :else   value))))
