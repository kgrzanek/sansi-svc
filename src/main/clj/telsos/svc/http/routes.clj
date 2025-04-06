(ns telsos.svc.http.routes
  (:require
   [clojure.string :as str]
   [ring.util.response :as ring-response]
   [telsos.lib.edn-json :refer [edn->json-string]]
   [telsos.lib.validation :refer [invalid]]
   [telsos.svc.http :refer [handler-body input->maybe-edn]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn- greeting-handler
  [request]
  (handler-body
    (let [{:keys [who-to-greet]} (input->maybe-edn request)]
      (when (str/blank? who-to-greet)
        (invalid "who-to-greet" {:who-to-greet who-to-greet}))

      (-> {:result (str "Hey " who-to-greet)}
          edn->json-string
          ring-response/response))))

(def routes
  [["/greeting"
    {:get {:handler  #'greeting-handler
           :produces ["application/json"]
           :consumes ["application/json"]}}]])
