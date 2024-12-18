(ns sansi.svc.auth.bearer-token
  (:require
   [buddy.sign.jwt :as jwt]
   [ring.util.response :as ring-response]
   [sansi.svc.secrets :as secrets]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.strings :refer [non-blank?]])
  (:import
   (java.security SecureRandom)
   (java.util Base64)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn generate-token-secret
  [length]
  (let [random (SecureRandom/getInstanceStrong)
        buffer (byte-array length)]
    (.nextBytes random buffer)
    (.encodeToString (Base64/getEncoder) buffer)))

(defn- expiration-24h []
  (+ (System/currentTimeMillis) (* 24 60 60 1000)))

(defonce ^:private secret (->> secrets/value :bearer-token-secret (the non-blank?)))

(defn create-token
  [user-id]
  (jwt/sign {:user-id user-id
             :exp     (expiration-24h)}

            secret))

(defn- parse-token [token]
  (when token
    (try
      (jwt/unsign token secret)
      (catch Exception _ nil))))

(defn wrap-bearer-token [handler]
  (fn [request]
    (let [^String auth-header
          (get-in request [:headers "authorization"])

          token
          (when auth-header
            (when (.startsWith auth-header "Bearer ")
              (.substring auth-header 7)))

          claims  (parse-token token)
          request (if-not claims request (assoc request :bearer-token-claims claims))]

      (handler request))))

(defn authenticated?
  [request]
  (:bearer-token-claims request))

(def ^:private not-authenticated-response
  (-> "Not authenticated with bearer token" ring-response/response (ring-response/status 401)))

(defn wrap-authenticated [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)

      not-authenticated-response)))
