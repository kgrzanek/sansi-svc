(ns telsos.svc.jdbc.datasources.postgres
  (:require
   [telsos.lib.exceptions :refer [catching->log-warn]]
   [telsos.svc.config.postgres]
   [telsos.svc.jdbc]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce main-postgres-datasource
  (delay (telsos.svc.jdbc/create-hikari-datasource
           "main-postgres-datasource"
           telsos.svc.config.postgres/main-db)))

(defonce test-postgres-datasource
  (delay (telsos.svc.jdbc/create-hikari-datasource
           "test-postgres-datasource"
           telsos.svc.config.postgres/test-db)))

;; NS FINALIZATION (for telsos-sysload)
(defn ns-finalize []
  (catching->log-warn (telsos.svc.jdbc/close-hikari! main-postgres-datasource))
  (catching->log-warn (telsos.svc.jdbc/close-hikari! test-postgres-datasource)))
