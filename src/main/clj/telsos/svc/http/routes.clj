(ns telsos.svc.http.routes
  (:require
   [clojure.string :as str]
   [telsos.lib.validation :refer [invalid]]
   [telsos.svc.http :refer [handler json-response parse-json-body]]))
;; =======
;;    [ring.util.response :as ring-response]
;;    [sansi.svc.auth.auth0 :as auth0]
;;    [sansi.svc.auth.bearer-token :as bearer-token]
;;    [telsos.svc.http :as http]))
;; >>>>>>> bea8838 (Integrated bearer token authentication, refactored auth0)

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn- greeting-handler
  [request]
  (handler
    (let [{:keys [who-to-greet]} (parse-json-body request)]
      (when (str/blank? who-to-greet)
        (throw (invalid "who-to-greet" {:who-to-greet who-to-greet})))

      (json-response {:result (str "Hey " who-to-greet)}))))

(def api-routes
  [["/api" {:middleware [bearer-token/wrap-authenticated]}
    ["/protected"
     {:get (fn [request]
             (ring-response/response
               {:message "Protected resource"
                :claims (:bearer-token-claims request)}))}]]])

(def html-routes
  [["/html" {:middleware [auth0/wrap-authenticated]}
    ["/" {:get (fn [request]
                 (ring-response/response
                   (pr-str "User: " (get-in request [:session :auth0-identity]))))}]]])

(def routes
  (-> []
      (into [["/test-1" {:get {:handler  #'handle-test-1
                               :produces ["application/json"]
                               :consumes ["application/json"]}}]])

      (into auth0/routes)
      (into api-routes)
      (into html-routes)))
