(ns telsos.svc.config
  (:require
   [clojure.string :as str]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.io]
   [telsos.lib.strings :refer [non-blank?]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce config
  (->> "config.edn" telsos.lib.io/read-resource-edn (the map?)))

(defonce secrets
  (->> ".secrets.edn" telsos.lib.io/read-resource-edn (the map?)))

(defonce commit-hash
  (->> ".commit_hash"
       telsos.lib.io/read-resource-str
       str/trim
       (the non-blank?)))

(def postgres-main-db
  {:jdbc-url (->> config  :postgres :main-db :jdbc-url (the non-blank?))
   :username (->> secrets :postgres :main-db :username (the non-blank?))
   :password (->> secrets :postgres :main-db :password (the non-blank?))})

(def postgres-test-db
  {:jdbc-url (->> config  :postgres :test-db :jdbc-url (the non-blank?))
   :username (->> secrets :postgres :test-db :username (the non-blank?))
   :password (->> secrets :postgres :test-db :password (the non-blank?))})
