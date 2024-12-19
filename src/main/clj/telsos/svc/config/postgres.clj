(ns telsos.svc.config.postgres
  (:require
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.strings :refer [non-blank?]]
   [telsos.svc.config :as config]
   [telsos.svc.secrets :as secrets]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(def main-db
  {:jdbc-url (->> config/value  :postgres :main-db :jdbc-url (the non-blank?))
   :username (->> secrets/value :postgres :main-db :username (the non-blank?))
   :password (->> secrets/value :postgres :main-db :password (the non-blank?))})

(def test-db
  {:jdbc-url (->> config/value  :postgres :test-db :jdbc-url (the non-blank?))
   :username (->> secrets/value :postgres :test-db :username (the non-blank?))
   :password (->> secrets/value :postgres :test-db :password (the non-blank?))})
