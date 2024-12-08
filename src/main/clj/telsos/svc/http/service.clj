(ns telsos.svc.http.service
  (:require
   [ring.middleware.flash]
   [ring.middleware.session]
   [sansi.svc.auth0]
   [telsos.svc.http :as http]
   [telsos.svc.http.routes :refer [routes]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce jetty
  (delay (http/jetty-start!
           (-> routes
               http/reitit-router
               http/reitit-handler
               sansi.svc.auth0/wrap-buddy-auth
               ring.middleware.session/wrap-session
               ring.middleware.flash/wrap-flash)

           {:port                 8080
            :join?                false
            :use-virtual-threads? true})))

;; NS FINALIZATION (for telsos-sysload)
(defn ns-finalize []
  (when (realized? jetty)
    (.close ^java.io.Closeable @jetty)))
