(ns telsos.svc.runtime-info
  (:require
   [telsos.lib.jvm]
   [telsos.lib.net]
   [telsos.lib.properties :refer [get-system-property]]
   [telsos.svc.commit-hash]))

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
  (println ":clj *assert*"                                         *assert*)
  (println ":clj clojure.compiler.direct-linking"            direct-linking)
  (println ":clj clojure.debug"                               clojure-debug)
  (println ":clj nrepl?"                                             nrepl?)
  (println ":clj telsos.svc.commit-hash/value" telsos.svc.commit-hash/value)
  (println "===="))
