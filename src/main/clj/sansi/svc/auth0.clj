(ns sansi.svc.auth0
  (:require
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.backends.session :refer [session-backend]]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [ring.util.response :as ring-response]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.logging :as log])
  (:import
   (com.auth0.client.auth AuthAPI)
   (com.auth0.json.auth TokenHolder UserInfo)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; AUTH-API
(defonce ^:private auth0-config
  (the map?
    (->> ".secrets.edn" io/resource slurp edn/read-string :auth0-config)))

(def ^:private auth0-client
  (AuthAPI. (:domain        auth0-config)
            (:client-id     auth0-config)
            (:client-secret auth0-config)))

(defn- login-url []
  (-> ^AuthAPI auth0-client
      (.authorizeUrl (:callback-url auth0-config))
      (.withScope "openid profile email")
      .build))

(defn- get-access-token
  ^TokenHolder [code]
  (-> ^AuthAPI auth0-client
      (.exchangeCode code (:callback-url auth0-config))
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

            user
            {:email   (.get values "email")
             :name    (.get values "name")
             :picture (.get values "picture")}]

        (-> (ring-response/redirect "/") (assoc :session (assoc session :identity user))))

      (catch Exception e
        (log/error e)
        (-> (ring-response/redirect "/auth0/login")
            (assoc :flash {:error "Authentication failed"}))))

    (ring-response/redirect "/auth0/login")))

(defn- handle-logout [_request]
  (-> (ring-response/redirect
        (format "https://%s/v2/logout?client_id=%s&returnTo=%s"
                (:domain           auth0-config)
                (:client-id        auth0-config)
                (:after-logout-url auth0-config)))

      (assoc :session nil)))

;; MIDDLEWARE
(def ^:private auth-backend (session-backend))

(defn- wrap-authenticated [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (-> (ring-response/redirect "/auth0/login")
          (assoc :flash {:error "Please, log in first"})))))

;; FACADE
(defn wrap-buddy-auth [handler]
  (-> handler
      (wrap-authentication auth-backend)
      (wrap-authorization  auth-backend)))

(def routes
  [["/auth0/login"    {:get handle-login}]
   ["/auth0/callback" {:get handle-callback}]
   ["/auth0/logout"   {:get handle-logout}]

   ["/api" {:middleware [wrap-authenticated]}
    ["/protected"
     {:get (fn [request]
             (ring-response/response
               {:message "Protected resource"
                :user    (get-in request [:session :identity])}))}]]
   ["/"
    {:get (fn [request]
            (if (authenticated? request)
              (ring-response/response "Welcome back")
              (ring-response/response "Please, login-in")))}]])
