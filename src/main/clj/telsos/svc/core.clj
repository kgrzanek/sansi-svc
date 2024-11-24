(ns telsos.svc.core
  (:require
   [telsos.svc.runtime-info :refer [print-runtime-info]])
  (:gen-class))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce ^:private _runtime-info (print-runtime-info))

;; MAIN
(defn -main [& args]
  (println "telsos.svc.core/main running" args))
