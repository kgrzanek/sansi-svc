(ns telsos.svc.http.service
  (:require
   [telsos.svc.http]
   [telsos.svc.http.routes :refer [routes]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defonce jetty
  (delay (telsos.svc.http/jetty-start!
           (-> routes
               telsos.svc.http/reitit-router
               telsos.svc.http/reitit-handler)

           {:port                 8080
            :join?                false
            :use-virtual-threads? true})))

;; NS FINALIZATION (for telsos-sysload)
(defn ns-finalize []
  (when (realized? jetty)
    (java.io.Closeable/.close @jetty)))
