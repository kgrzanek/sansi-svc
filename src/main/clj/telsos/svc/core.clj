(ns telsos.svc.core
  (:require
   [telsos.lib.jvm]
   [telsos.lib.net]
   [telsos.lib.properties :refer [get-system-property]]
   [telsos.svc.config]
   [telsos.svc.http.service]
   [telsos.svc.jdbc.datasources.postgres]
   [telsos.svc.logging.logback])
  (:gen-class))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce clojure-debug  (get-system-property Boolean "clojure.debug"                   {:default false}))
(defonce direct-linking (get-system-property Boolean "clojure.compiler.direct-linking" {:default false}))
(defonce java-version   (get-system-property String "java.version"))
(defonce jvm-args       (-> ["-Xms" "-Xmx" "-XX:" "-Xlog"] telsos.lib.jvm/jvm-args sort))
(defonce nrepl?         (telsos.lib.net/nrepl? "127.0.0.1" 7888))

(defn print-runtime-info []
  (println "====")
  (println ":jvm version" java-version)
  (doseq [arg jvm-args] (println ":jvm" arg))
  (println)
  (println ":clj *assert*"                                           *assert*)
  (println ":clj clojure.compiler.direct-linking"              direct-linking)
  (println ":clj clojure.debug"                                 clojure-debug)
  (println ":clj nrepl?"                                               nrepl?)
  (println ":clj telsos.svc.config/commit-hash" telsos.svc.config/commit-hash)
  (println "===="))

;; MAIN
(defn -main [& args]
  (telsos.svc.logging.logback/configure-FILE-appender! "default")
  (force telsos.svc.jdbc.datasources.postgres/main-datasource)
  (force telsos.svc.http.service/jetty)
  (println "telsos.svc.core/main running" :args args))
