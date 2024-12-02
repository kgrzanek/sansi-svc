(ns telsos.svc.http
  (:require
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]
   [reitit.ring.middleware.muuntaja :as reitit-muuntaja]
   [reitit.ring.middleware.parameters :as reitit-parameters]
   [ring.adapter.jetty :as ring-jetty]
   [ring.middleware.json]
   [ring.util.response :as ring-response]
   [telsos.lib.assertions :refer [the]])
  (:import
   (java.util.concurrent Executors)
   (org.eclipse.jetty.util.thread QueuedThreadPool)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; ROUTER, HANDLER, MIDDLEWARE
(defn reitit-router
  [routes]
  (reitit-ring/router
    routes
    {:data {:middleware
            [reitit-parameters/parameters-middleware
             reitit-muuntaja/format-middleware
             [ring.middleware.json/wrap-json-body
              {:keywords?    true
               :bigdecimals? true}]
             ring.middleware.json/wrap-json-response]}}))

(defn reitit-handler
  ([router]
   (reitit-handler router #_options nil))

  ([router {:keys [resources-path resources-root] :as options}]
   (reitit-ring/ring-handler
     router

     ;; The default handler
     (reitit-ring/routes
       (reitit-ring/redirect-trailing-slash-handler)

       (when resources-path
         (reitit-ring/create-resource-handler
           (merge {:path resources-path} (when resources-root {:root resources-root}))))

       (reitit-ring/create-default-handler))

     ;; The other options are std. reitit-ring options
     (dissoc options :resources-path :resources-root))))

;; JETTY
(declare jetty-stop!)

(defrecord ^:private Jetty [jetty]
  java.io.Closeable
  (close [_this] (jetty-stop! jetty)))

(defn jetty-start!
  [handler {:keys [port join? use-virtual-threads?]
            :or   {port                 8080
                   join?                false
                   use-virtual-threads? false}}]

  (assert (pos-int? port))
  (let [jetty
        (ring-jetty/run-jetty
          handler

          #_options
          (merge {:port  port
                  :join? (boolean join?)

                  ;; 10MB
                  :max-form-content-size 10485760}

                 (when use-virtual-threads?
                   {:thread-pool
                    (doto (QueuedThreadPool.)
                      (.setVirtualThreadsExecutor
                        (Executors/newVirtualThreadPerTaskExecutor)))})))]

    (log/info jetty "started")

    (->Jetty jetty)))

(defn- jetty-stop!
  [^org.eclipse.jetty.server.Server jetty]
  (if (.isRunning jetty)
    (do (.stop jetty)
        (log/info jetty "successfully stopped"))

    (log/info jetty "has already been stopped")))

;; STD. HANDLER BODY
(defn handler-body* [body]
  (try
    (the ring-response/response? (body))
    (catch telsos.lib.ValidationException e
      (log/debug e "ValidationException in handler-body")
      (ring-response/bad-request {:reason "non-disclosed"}))))

(defmacro handler-body [& body]
  `(handler-body* (fn [] ~@body)))
