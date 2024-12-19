(ns telsos.svc.secrets
  (:require
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.io :as io]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce value
  (->> ".secrets.edn" io/read-resource-edn (the map?)))
