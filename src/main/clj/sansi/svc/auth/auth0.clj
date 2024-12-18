(ns sansi.svc.auth.auth0
  (:require
   [ring.util.response :as ring-response]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.logging :as log]
   [telsos.lib.strings :refer [non-blank?]]
   [telsos.svc.config :as config]
   [telsos.svc.secrets :as secrets])
  (:import
   (com.auth0.client.auth AuthAPI)
   (com.auth0.json.auth TokenHolder UserInfo)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; AUTH-API
(defonce ^:private secrets (->> secrets/value :auth0 (the map?)))

(defonce ^:private config
  (let [base-url (->> config/value :base-url (the non-blank?))]
    {:callback-url     (str base-url "/auth0/callback")
     :after-logout-url base-url}))

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
(defn- handle-login
  [_request]
  (ring-response/redirect (login-url)))

(defn- handle-callback
  [{:keys [params session]}]
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

            session (assoc session :auth0-identity identity)]

        (assoc (ring-response/redirect "/") :session session))

      (catch Exception e
        (log/error e)
        (-> (ring-response/redirect "/auth0/login")
            (assoc :flash {:error "Authentication failed"}))))

    (ring-response/redirect "/auth0/login")))

(defn- handle-logout
  [_request]
  (-> (ring-response/redirect
        (format "https://%s/v2/logout?client_id=%s&returnTo=%s"
                (:domain           secrets)
                (:client-id        secrets)
                (:after-logout-url  config)))

      (assoc :session nil)))

(def routes
  [["/auth0"
    ["/login"    {:get handle-login}]
    ["/callback" {:get handle-callback}]
    ["/logout"   {:get handle-logout}]]])

;; FACADE (INCL. MIDDLEWARE)
(def ^:private not-authenticated-response
  (-> "Not authenticated with auth0" ring-response/response (ring-response/status 401)))

(defn authenticated?
  [request]
  (-> request :session :auth0-identity))

(defn wrap-authenticated [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)

      not-authenticated-response)))
