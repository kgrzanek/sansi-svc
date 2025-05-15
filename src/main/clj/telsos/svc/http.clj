(ns telsos.svc.http
  (:require
   [reitit.ring]
   [reitit.ring.middleware.muuntaja]
   [reitit.ring.middleware.parameters]
   [ring.adapter.jetty]
   [ring.util.response]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.edn-json]
   [telsos.lib.logging :as log]
   [telsos.lib.strings :refer [non-blank?]])
  (:import
   (java.util.concurrent Executors)
   (org.eclipse.jetty.server Server)
   (org.eclipse.jetty.util.thread QueuedThreadPool)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; ROUTER, HANDLER, MIDDLEWARE
(defn reitit-router
  [routes]
  (reitit.ring/router
    routes
    {:data {:middleware
            [reitit.ring.middleware.parameters/parameters-middleware
             reitit.ring.middleware.muuntaja/format-middleware]}}))

(defn reitit-handler
  ([router]
   (reitit-handler router #_options nil))

  ([router {:keys [resources-path resources-root] :as options}]
   (reitit.ring/ring-handler
     router

     ;; The default handler
     (reitit.ring/routes
       (reitit.ring/redirect-trailing-slash-handler)

       (when resources-path
         (reitit.ring/create-resource-handler
           (merge {:path resources-path}
                  (when resources-root {:root resources-root}))))

       (reitit.ring/create-default-handler))

     ;; The other options are std. reitit.ring options
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
        (ring.adapter.jetty/run-jetty
          handler

          #_options
          (merge
            {:port  port
             :join? (boolean join?)

             ;; 10MB
             :max-form-content-size 10485760}

            (when use-virtual-threads?
              {:thread-pool
               (doto (QueuedThreadPool.)
                 (QueuedThreadPool/.setVirtualThreadsExecutor
                   (Executors/newVirtualThreadPerTaskExecutor)))})))]

    (log/info jetty "started")

    (->Jetty jetty)))

(defn- jetty-stop!
  [jetty]
  (if (Server/.isRunning jetty)
    (do (Server/.stop jetty)
        (log/info jetty "successfully stopped"))

    (log/info jetty "has already been stopped")))

;; STD. HANDLER BODY
(defn handler* [body]
  (try
    (the ring.util.response/response? (body))

    (catch telsos.lib.ValidationException e
      (log/debug e)
      (ring.util.response/bad-request "telsos.lib.ValidationException"))

    (catch Throwable e
      (log/error e)
      (ring.util.response/status
        (ring.util.response/response "Internal Server Error") 500))))

(defmacro handler [& body]
  `(handler* (fn [] ~@body)))

;; JSON CONSUMING/PRODUCING
(defn parse-json-body
  [{:keys [body] :as _request}]
  (when body
    (let [json-body (slurp body)]
      (when (non-blank? json-body)
        (telsos.lib.edn-json/json-string->edn json-body)))))

(defn json-response
  ([edn]
   (-> edn
       telsos.lib.edn-json/edn->json-string
       ring.util.response/response))

  ([status edn]
   (the nat-int? status)
   (-> edn
       telsos.lib.edn-json/edn->json-string
       ring.util.response/response
       (ring.util.response/status status))))
