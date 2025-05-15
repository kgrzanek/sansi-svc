(ns telsos.svc.commit-hash
  (:require
   [clojure.string :as str]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.io]
   [telsos.lib.strings :refer [non-blank?]]))

(defonce value
  (->> ".commit_hash"
       telsos.lib.io/read-resource-str
       str/trim
       (the non-blank?)))
