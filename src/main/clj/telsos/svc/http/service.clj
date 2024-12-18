(ns telsos.svc.http.service
  (:require
   [telsos.svc.http]
;; =======
;;    [ring.middleware.flash]
;;    [ring.middleware.session]
;;    [sansi.svc.auth.auth0]
;;    [sansi.svc.auth.bearer-token]
;;    [telsos.svc.http :as http]
;; >>>>>>> bea8838 (Integrated bearer token authentication, refactored auth0)
   [telsos.svc.http.routes :refer [routes]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce jetty
  (delay (telsos.svc.http/jetty-start!
           (-> routes
               telsos.svc.http/reitit-router
               telsos.svc.http/reitit-handler)
               ring.middleware.session/wrap-session
               sansi.svc.auth.bearer-token/wrap-bearer-token
               ring.middleware.flash/wrap-flash)

           {:port                 8080
            :join?                false
            :use-virtual-threads? true})))

;; NS FINALIZATION (for telsos-sysload)
(defn ns-finalize []
  (when (realized? jetty)
    (java.io.Closeable/.close @jetty)))
