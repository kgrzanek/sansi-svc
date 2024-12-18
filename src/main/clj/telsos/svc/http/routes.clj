(ns telsos.svc.http.routes
  (:require
   [ring.util.response :as ring-response]
   [sansi.svc.auth.auth0 :as auth0]
   [sansi.svc.auth.bearer-token :as bearer-token]
   [telsos.svc.http :as http]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn- handle-test-1
  [_request]
  (http/handler-body (ring-response/response {:result "OK"})))

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
