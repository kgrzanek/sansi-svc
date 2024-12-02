(ns telsos.svc.core
  (:require
   [telsos.svc.http.service]
   [telsos.svc.jdbc.datasources.postgres]
   [telsos.svc.logging.logback]
   [telsos.svc.runtime-info :refer [print-runtime-info]])
  (:gen-class))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce ^:private _runtime-info (print-runtime-info))

;; MAIN
(defn -main [& args]
  (telsos.svc.logging.logback/configure-FILE-appender! "default")
  (force telsos.svc.jdbc.datasources.postgres/main-postgres-datasource)
  (force telsos.svc.http.service/jetty)
  (println "telsos.svc.core/main running" :args args))
