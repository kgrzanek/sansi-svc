(ns telsos.svc.jdbc.datasources.postgres
  (:require
   [telsos.lib.exceptions :refer [catching->log-warn]]
   [telsos.svc.config.postgres :as config]
   [telsos.svc.jdbc :as jdbc]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce main-postgres-datasource
  (delay (jdbc/create-hikari-datasource
           "main-postgres-datasource" config/main-db)))

(defonce test-postgres-datasource
  (delay (jdbc/create-hikari-datasource
           "test-postgres-datasource" config/test-db)))

;; NS FINALIZATION (for kongra/telsos-sysload)
(defn ns-finalize []
  (-> main-postgres-datasource jdbc/close-hikari! catching->log-warn)
  (-> test-postgres-datasource jdbc/close-hikari! catching->log-warn))
