(ns sansi.svc.auth0
  (:require
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.backends.session :refer [session-backend]]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [ring.util.response :as ring-response]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.io :as io]
   [telsos.lib.logging :as log])
  (:import
   (com.auth0.client.auth AuthAPI)
   (com.auth0.json.auth TokenHolder UserInfo)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; AUTH-API
(defonce ^:private secrets
  (->> ".secrets.edn" io/read-resource-edn :auth0 (the map?)))

(defonce ^:private config
  {:callback-url     "https://745a-37-30-48-41.ngrok-free.app/auth0/callback"
   :after-logout-url "https://745a-37-30-48-41.ngrok-free.app"})

(def ^:private auth0-client
  (AuthAPI. (:domain        secrets)
            (:client-id     secrets)
            (:client-secret secrets)))

(defn- login-url []
  (-> ^AuthAPI auth0-client
      (.authorizeUrl (:callback-url config))
      (.withScope "openid profile email")
      .build))

(defn- get-access-token
  ^TokenHolder [code]
  (-> ^AuthAPI auth0-client
      (.exchangeCode code (:callback-url config))
      .execute
      .getBody))

(defn- get-user-info
  ^UserInfo [access-token]
  (-> ^AuthAPI auth0-client
      (.userInfo access-token)
      .execute
      .getBody))

;; AUTHENTICATION HANDLERS
(defn- handle-login [_request]
  (ring-response/redirect (login-url)))

(defn- handle-callback [{:keys [params session]}]
  (if-let [code (or (get params :code) (get params "code"))]
    (try
      (let [token-response (get-access-token          code)
            access-token   (.getAccessToken token-response)
            user-info      (get-user-info     access-token)
            values         (.getValues           user-info)

            identity
            {:email   (.get values "email")
             :name    (.get values "name")
             :picture (.get values "picture")}

            session (assoc session :identity identity)]

        (assoc (ring-response/redirect "/") :session session))

      (catch Exception e
        (log/error e)
        (-> (ring-response/redirect "/auth0/login")
            (assoc :flash {:error "Authentication failed"}))))

    (ring-response/redirect "/auth0/login")))

(defn- handle-logout [_request]
  (-> (ring-response/redirect
        (format "https://%s/v2/logout?client_id=%s&returnTo=%s"
                (:domain           secrets)
                (:client-id        secrets)
                (:after-logout-url  config)))

      (assoc :session nil)))

;; MIDDLEWARE
(defn wrap-authenticated [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)

      {:status  401
       :headers {}
       :body    "Not authenticated"})))

(def ^:private auth-backend (session-backend))

(defn wrap-session-auth [handler]
  (-> handler
      (wrap-authentication auth-backend)
      (wrap-authorization  auth-backend)))

(def routes
  [["/auth0"
    ["/login"    {:get handle-login}]
    ["/callback" {:get handle-callback}]
    ["/logout"   {:get handle-logout}]]

   ["/api" {:middleware [wrap-authenticated]}
    ["/protected"
     {:get (fn [request]
             (ring-response/response
               {:message "Protected resource"
                :user    (get-in request [:session :identity])}))}]]
   ["/"
    {:get (fn [request]
            (if (authenticated? request)
              (ring-response/response (pr-str "User: " (get-in request [:session :identity])))
              (ring-response/response "Please, login-in")))}]])
