(ns telsos.svc.config.postgres)

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(def main-db
  {:jdbc-url "jdbc:postgresql://localhost:5501/telsos?currentSchema=telsos"
   :username "telsos_owner"
   :password "12345"})

(def test-db
  {:jdbc-url "jdbc:postgresql://localhost:5502/telsos?currentSchema=telsos"
   :username "telsos_owner"
   :password "12345"})
