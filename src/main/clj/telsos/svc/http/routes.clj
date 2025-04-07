(ns telsos.svc.http.routes
  (:require
   [clojure.string :as str]
   [telsos.lib.validation :refer [invalid]]
   [telsos.svc.http :refer [handler json-response parse-json-body]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn- greeting-handler
  [request]
  (handler
    (let [{:keys [who-to-greet]} (parse-json-body request)]
      (when (str/blank? who-to-greet)
        (throw (invalid "who-to-greet" {:who-to-greet who-to-greet})))

      (json-response {:result (str "Hey " who-to-greet)}))))

(def routes
  [["/greeting"
    {:get {:handler  #'greeting-handler
           :produces ["application/json"]
           :consumes ["application/json"]}}]])
