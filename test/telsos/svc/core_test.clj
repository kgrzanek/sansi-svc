(ns telsos.svc.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [telsos.svc.core :as core]))

(deftest test-main
  (testing "-main"
    (is (= "telsos.svc.core/main running :args (-arg-1 --arg-2)\n"
           (with-out-str (core/-main "-arg-1" "--arg-2"))))))
