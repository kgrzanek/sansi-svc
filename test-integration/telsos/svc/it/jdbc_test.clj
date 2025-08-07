(ns telsos.svc.it.jdbc-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [hugsql.core :as hug]
   [telsos.svc.jdbc :as jdbc]
   [telsos.svc.jdbc.datasources.postgres :refer [main-datasource test-datasource]]
   [telsos.svc.jdbc.postgres :as pg]
   [telsos.svc.migrations :as migrations])
  (:import
   (java.sql Connection)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; RUNNER OF THE DATASOURCE SERVICES. RUNS WHEN YOU CALL @boot WHEN IN nrepl.
(force @main-datasource)
(force @test-datasource)

;; TESTS
(use-fixtures :once (migrations/fixture @test-datasource {:only-up? true}))

(declare all-test1-data inc-test1-val!)
(hug/def-db-fns "test1.sql" {:quoting :ansi})

(deftest transaction-isolation-levels-test
  (testing "read-committed"

    (is (jdbc/no-transaction?))
    (is (jdbc/no-transaction! true))

    (->> (is (jdbc/in-transaction?))
         (jdbc/serializable [@test-datasource])
         (jdbc/restarting {:times                  0
                           :serialization-failure? pg/serialization-failure?}))

    (->> (is (thrown? IllegalStateException (jdbc/no-transaction! true)))
         (jdbc/serializable  [@test-datasource])
         (jdbc/restarting {:times                  0
                           :serialization-failure? pg/serialization-failure?}))

    (is (= Connection/TRANSACTION_READ_COMMITTED
           (->> (Connection/.getTransactionIsolation @jdbc/t-conn*)
                (jdbc/read-committed [@test-datasource])
                (jdbc/restarting  {:times                  0
                                   :serialization-failure? pg/serialization-failure?}))))

    (is (= 3 (->> @jdbc/t-conn*
                  all-test1-data
                  (map :id)
                  vec
                  count
                  ;; [1] The usage of a datasource goes like that:
                  (jdbc/read-committed [@test-datasource])
                  (jdbc/restarting  {:times                  0
                                     :serialization-failure? pg/serialization-failure?})))))

  (testing "repeatable-read"
    (is (= Connection/TRANSACTION_REPEATABLE_READ
           (->> (Connection/.getTransactionIsolation @jdbc/t-conn*)
                (jdbc/repeatable-read [@test-datasource])
                (jdbc/restarting {:times                  0
                                  :serialization-failure? pg/serialization-failure?}))))

    (is (= 3 (->> @jdbc/t-conn*
                  all-test1-data
                  (map :id)
                  vec
                  count
                  (jdbc/repeatable-read [@test-datasource])
                  (jdbc/restarting {:times                  1
                                    :serialization-failure? pg/serialization-failure?})))))

  (testing "serializable"
    (is (= Connection/TRANSACTION_SERIALIZABLE
           (->> (Connection/.getTransactionIsolation @jdbc/t-conn*)
                (jdbc/serializable  [@test-datasource])
                (jdbc/restarting {:times                  0
                                  :serialization-failure? pg/serialization-failure?}))))

    (is (= 3 (->> @jdbc/t-conn*
                  all-test1-data
                  (map :id)
                  vec
                  count
                  (jdbc/serializable  [@test-datasource])
                  (jdbc/restarting {:times                  1
                                    :serialization-failure? pg/serialization-failure?}))))))

(defn- inc-test1-val-with-delay!
  [id delay-msecs]
  (let [result (inc-test1-val! @jdbc/t-conn* {:id (long id)})]
    (Thread/sleep (long delay-msecs))
    result))

(deftest serializable-restarts-test
  (testing "simple update, no restarts behavior"
    (is (= 1 (->> (inc-test1-val-with-delay! 1 1)
                  (jdbc/serializable [@test-datasource])
                  (jdbc/restarting {:times                  0
                                    :serialization-failure? pg/serialization-failure?})))))

  (testing "restart of tx1 in future-1"
    (let [events-1-atom (atom [])
          future-1
          (future
            (->> (inc-test1-val-with-delay! 1 100 #_msecs)
                 (jdbc/serializable  [@test-datasource])
                 (jdbc/restarting
                   {:times                  0
                    :events-handler         #(swap! events-1-atom conj %)
                    :serialization-failure? pg/serialization-failure?})))

          _ (Thread/sleep 50 #_msecs)

          events-2-atom (atom [])
          future-2
          (future
            (->> (inc-test1-val-with-delay! 1 1 #_msecs)
                 (jdbc/serializable  [@test-datasource])
                 (jdbc/restarting
                   {:times                  1
                    :events-handler         #(swap! events-2-atom conj %)
                    :serialization-failure? pg/serialization-failure?})))]

      (is (= 1 @future-1))
      (is (= 1 @future-2))

      (is (= [{:attempt-last 0}
              {:result       1}]

             (map #(dissoc % :thread-id :instant) @events-1-atom)))

      (is (= [{:attempt      0}
              {:attempt-last 1}
              {:result       1}]

             (map #(dissoc % :thread-id :instant) @events-2-atom))))))
