(ns telsos.svc.it.http-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [telsos.svc.http.service :refer [jetty]]))

;; RUNNER OF THE JETTY SERVICE WHEN IN nrepl
(force @jetty)

(deftest test-jetty
  (testing "jetty"
    (is (some? @jetty))))
