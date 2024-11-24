(ns telsos.commit-hash
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- read-commit-hash []
  (-> (io/resource ".commit_hash") slurp str/trim))

(defonce value
  (let [commit-hash (read-commit-hash)]
    (assert (not (str/blank? commit-hash)))
    commit-hash))
