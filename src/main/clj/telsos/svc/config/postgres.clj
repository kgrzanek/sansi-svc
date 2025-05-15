(ns telsos.svc.config.postgres
  (:require
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.strings :refer [non-blank?]]
   [telsos.svc.config]
   [telsos.svc.secrets]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(def main-db
  {:jdbc-url (->> telsos.svc.config/value  :postgres :main-db :jdbc-url (the non-blank?))
   :username (->> telsos.svc.secrets/value :postgres :main-db :username (the non-blank?))
   :password (->> telsos.svc.secrets/value :postgres :main-db :password (the non-blank?))})

(def test-db
  {:jdbc-url (->> telsos.svc.config/value  :postgres :test-db :jdbc-url (the non-blank?))
   :username (->> telsos.svc.secrets/value :postgres :test-db :username (the non-blank?))
   :password (->> telsos.svc.secrets/value :postgres :test-db :password (the non-blank?))})
