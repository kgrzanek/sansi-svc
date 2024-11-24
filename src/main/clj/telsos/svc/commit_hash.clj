(ns telsos.svc.commit-hash
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.strings :refer [non-blank?]]))

(defonce value
  (->> ".commit_hash" io/resource  slurp str/trim (the non-blank?)))
