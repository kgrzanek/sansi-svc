(ns telsos.svc.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   ;; [telsos.lib.edn-json :as json]
   [telsos.svc.core :as core]))

(deftest test-main
  (testing "-main"
    (is (= "telsos.svc.core/main running :args (-arg-1 --arg-2)\n"
           (with-out-str (core/-main "-arg-1" "--arg-2"))))))

;; (def test-data-string
;;   (slurp "/home/kongra/Devel/hot/golang/mygo/sample_data.json"))

;; (def test-data (json/json-string->edn test-data-string))

;; (use 'criterium.core)

;; (quick-bench (json/json-string->edn test-data-string))
;; (quick-bench (json/edn->json-string test-data))
