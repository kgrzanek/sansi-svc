(ns telsos.svc.jdbc.datasources.postgres
  (:require
   [telsos.lib.exceptions :refer [catching->log-warn]]
   [telsos.svc.config]
   [telsos.svc.jdbc]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce main-datasource
  (delay (telsos.svc.jdbc/create-hikari-datasource
           "postgres/main-datasource"
           telsos.svc.config/postgres-main-db)))

(defonce test-datasource
  (delay (telsos.svc.jdbc/create-hikari-datasource
           "postgres/test-datasource"
           telsos.svc.config/postgres-test-db)))

;; NS FINALIZATION (for telsos-sysload)
(defn ns-finalize []
  (catching->log-warn (telsos.svc.jdbc/close-hikari! main-datasource))
  (catching->log-warn (telsos.svc.jdbc/close-hikari! test-datasource)))
