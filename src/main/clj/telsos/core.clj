(ns telsos.core
  (:require
   [telsos.core.runtime-info :refer [print-runtime-info]])
  (:gen-class))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce ^:private _runtime-info (print-runtime-info))

;; MAIN
(defn -main [& args]
  (println "telsos.core/main running" args))
