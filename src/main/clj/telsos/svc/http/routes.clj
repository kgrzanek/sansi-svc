(ns telsos.svc.http.routes
  (:require
   [ring.util.response :as ring-response]
   [sansi.svc.auth0 :as auth0]
   [telsos.svc.http :as http]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn- handle-test-1
  [_request]
  (http/handler-body (ring-response/response {:result "OK"})))

(def routes
  (into
    [["/test-1" {:get {:handler  #'handle-test-1
                       :produces ["application/json"]
                       :consumes ["application/json"]}}]]

    auth0/routes))
