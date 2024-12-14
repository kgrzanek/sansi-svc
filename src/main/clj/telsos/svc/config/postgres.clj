(ns telsos.svc.config.postgres
  (:require
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.io :as io]
   [telsos.lib.strings :refer [non-blank?]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce ^:private secrets
  (->> ".secrets.edn" io/read-resource-edn :postgres (the map?)))

(def main-db
  {:jdbc-url "jdbc:postgresql://localhost:5501/telsos?currentSchema=telsos"
   :username (->> secrets :username (the non-blank?))
   :password (->> secrets :password (the non-blank?))})

(def test-db
  {:jdbc-url "jdbc:postgresql://localhost:5502/telsos?currentSchema=telsos"
   :username (->> secrets :username (the non-blank?))
   :password (->> secrets :password (the non-blank?))})
