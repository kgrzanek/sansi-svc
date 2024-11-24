(ns jdbc-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [hugsql.core :as hug]
   [telsos.svc.jdbc :as jdbc]
   [telsos.svc.jdbc.datasources.postgres :refer [test-postgres-datasource]]
   [telsos.svc.jdbc.postgres :as pg]
   [telsos.svc.migrations :as migrations])
  (:import
   (java.sql Connection)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(use-fixtures :once (migrations/fixture @test-postgres-datasource {:only-up? true}))

(declare all-test1-data inc-test1-val!)
(hug/def-db-fns "test1.sql" {:quoting :ansi})

(deftest transaction-isolation-levels-test
  (testing "read-committed"

    (is (jdbc/no-transaction?))
    (is (jdbc/no-transaction! true))

    (->> (is (jdbc/in-transaction?))
         (jdbc/serializable [@test-postgres-datasource])
         (pg/restarting {:times 0}))

    (->> (is (thrown? IllegalStateException (jdbc/no-transaction! true)))
         (jdbc/serializable  [@test-postgres-datasource])
         (pg/restarting {:times 0}))

    (is (= Connection/TRANSACTION_READ_COMMITTED
           (->> (.getTransactionIsolation ^Connection @jdbc/t-conn*)
                (jdbc/read-committed [@test-postgres-datasource])
                (pg/restarting  {:times 0}))))

    (is (= 3 (->> @jdbc/t-conn*
                  all-test1-data
                  (map :id)
                  vec
                  count
                  ;; [1] The usage of a datasource goes like that:
                  (jdbc/read-committed [@test-postgres-datasource])
                  (pg/restarting  {:times 0})))))

  (testing "repeatable-read"
    (is (= Connection/TRANSACTION_REPEATABLE_READ
           (->> (.getTransactionIsolation ^Connection @jdbc/t-conn*)
                (jdbc/repeatable-read [@test-postgres-datasource])
                (pg/restarting   {:times 0}))))

    (is (= 3 (->> @jdbc/t-conn*
                  all-test1-data
                  (map :id)
                  vec
                  count
                  (jdbc/repeatable-read [@test-postgres-datasource])
                  (pg/restarting   {:times 1})))))

  (testing "serializable"
    (is (= Connection/TRANSACTION_SERIALIZABLE
           (->> (.getTransactionIsolation ^Connection @jdbc/t-conn*)
                (jdbc/serializable  [@test-postgres-datasource])
                (pg/restarting {:times 0}))))

    (is (= 3 (->> @jdbc/t-conn*
                  all-test1-data
                  (map :id)
                  vec
                  count
                  (jdbc/serializable  [@test-postgres-datasource])
                  (pg/restarting {:times 1}))))))

(defn- inc-test1-val-with-delay!
  [id delay-msecs]
  (let [result (inc-test1-val! @jdbc/t-conn* {:id (long id)})]
    (Thread/sleep (long delay-msecs))
    result))

(deftest serializable-restarts-test
  (testing "simple update, no restarts behavior"
    (is (= 1 (->> (inc-test1-val-with-delay! 1 1)
                  (jdbc/serializable  [@test-postgres-datasource])
                  (pg/restarting {:times 0})))))

  (testing "restart of tx1 in future-1"
    (let [events-atom    (atom [])
          events-handler (fn [event] (swap! events-atom conj event))

          future-1
          (->> (inc-test1-val-with-delay! 1 100 #_msecs)
               (jdbc/serializable  [@test-postgres-datasource])
               (pg/restarting {:times 0 :events-handler events-handler})
               (future))

          _ (Thread/sleep 50 #_msecs)

          future-2
          (->> (inc-test1-val-with-delay! 1 1 #_msecs)
               (jdbc/serializable  [@test-postgres-datasource])
               (pg/restarting {:times 1 :events-handler events-handler})
               (future))]

      (is (= 1 @future-1))
      (is (= 1 @future-2))

      (let [events (map #(dissoc % :thread-id :instant) @events-atom)]
        (is (= [{:last-attempt 0} {:attempt 0} {:return 1} {:last-attempt 1} {:return 1}] events))))))
