(ns jdbc-test
  (:require
   #_[clojure.test :refer [deftest is testing]]
   [hugsql.core :as hug]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

#_(use-fixtures :once (local-test-postgres-migrations-fixture :only-up))

(declare all-test1-data inc-test1-val!)
(hug/def-db-fns "test1.sql" {:quoting :ansi})

#_(let [k :local-test-postgres]
    ;; This use-case represents our preferred way of using databases at the
    ;; implementation level. They are named Hikari CP datasources managed as
    ;; singletons by resoreg. [1]
    (resoreg/defresource! k
      (delay (jdbc/->hikari-named-datasource k config/local-test-postgres))))

#_(deftest transaction-isolation-levels-test
    (testing "read-committed"

      (is (jdbc/no-transaction?))
      (is (jdbc/no-transaction! true))

      (->> (is (jdbc/in-transaction?))
           (serializable  [(resoreg/resource :local-test-postgres)])
           (pg-restarting {:times 0}))

      (->> (is (thrown? IllegalStateException (jdbc/no-transaction! true)))
           (serializable  [(resoreg/resource :local-test-postgres)])
           (pg-restarting {:times 0}))

      (is (= Connection/TRANSACTION_READ_COMMITTED
             (->> (.getTransactionIsolation ^Connection @t-conn*)
                  (read-committed [(resoreg/resource :local-test-postgres)])
                  (pg-restarting  {:times 0}))))

      (is (= 3 (->> @t-conn*
                    all-test1-data
                    (map :id)
                    vec
                    count
                    ;; [1] The usage of a datasource goes like that:
                    (read-committed [(resoreg/resource :local-test-postgres)])
                    (pg-restarting  {:times 0})))))

    (testing "repeatable-read"
      (is (= Connection/TRANSACTION_REPEATABLE_READ
             (->> (.getTransactionIsolation ^Connection @t-conn*)
                  (repeatable-read [(resoreg/resource :local-test-postgres)])
                  (pg-restarting   {:times 0}))))

      (is (= 3 (->> @t-conn*
                    all-test1-data
                    (map :id)
                    vec
                    count
                    (repeatable-read [(resoreg/resource :local-test-postgres)])
                    (pg-restarting   {:times 1})))))

    (testing "serializable"
      (is (= Connection/TRANSACTION_SERIALIZABLE
             (->> (.getTransactionIsolation ^Connection @t-conn*)
                  (serializable  [(resoreg/resource :local-test-postgres)])
                  (pg-restarting {:times 0}))))

      (is (= 3 (->> @t-conn*
                    all-test1-data
                    (map :id)
                    vec
                    count
                    (serializable  [(resoreg/resource :local-test-postgres)])
                    (pg-restarting {:times 1}))))))

#_(defn- inc-test1-val-with-delay!
    [id delay-msecs]
    (let [result (inc-test1-val! @t-conn* {:id (long id)})]
      (Thread/sleep (long delay-msecs))
      result))

#_(deftest serializable-restarts-test
    (testing "simple update, no restarts behavior"
      (is (= 1 (->> (inc-test1-val-with-delay! 1 1)
                    (serializable  [(resoreg/resource :local-test-postgres)])
                    (pg-restarting {:times 0})))))

    (testing "restart of tx1 in future-1"
      (let [events-atom    (atom [])
            events-handler (fn [event] (swap! events-atom conj event))

            future-1
            (->> (inc-test1-val-with-delay! 1 100 #_msecs)
                 (serializable  [(resoreg/resource :local-test-postgres)])
                 (pg-restarting {:times 0 :events-handler events-handler})
                 (future))

            _ (Thread/sleep 50 #_msecs)

            future-2
            (->> (inc-test1-val-with-delay! 1 1 #_msecs)
                 (serializable  [(resoreg/resource :local-test-postgres)])
                 (pg-restarting {:times 1 :events-handler events-handler})
                 (future))]

        (is (= 1 @future-1))
        (is (= 1 @future-2))

        (let [events (map #(dissoc % :thread-id :instant) @events-atom)]
          (is (= [{:last-attempt 0} {:attempt 0} {:return 1} {:last-attempt 1} {:return 1}] events))))))
