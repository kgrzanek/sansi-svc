(ns sansi.svc.auth.bearer-token
  (:require
   [buddy.sign.jwt :as jwt]
   [ring.util.response :as ring-response]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.strings :refer [non-blank?]]
   [telsos.svc.config :as config]
   [tick.core :as t])
  (:import
   (java.security SecureRandom)
   (java.util Base64)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn generate-token-secret
  [^long length]
  (the nat-int? length)
  (let [random (SecureRandom/getInstanceStrong)
        buffer (byte-array length)]
    (.nextBytes random buffer)
    (.encodeToString (Base64/getEncoder) buffer)))

(defonce ^:private secret (->> config/secrets :bearer-token-secret (the non-blank?)))

(defn create-token
  ([user-id duration-value duration-unit]
   (create-token user-id
                 (+ (System/currentTimeMillis)
                    (-> (t/new-duration duration-value duration-unit) t/millis long))))

  ([user-id expiration-msecs]
   (the nat-int? expiration-msecs)
   (jwt/sign {:user-id          user-id
              :expiration-msecs expiration-msecs}

             secret)))

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

          claims
          (parse-token token)

          request
          (cond-> request
            (some? claims) (assoc :bearer-token-claims claims))]

      (handler request))))

(defn- authenticated?
  [request]
  (when-let [{:keys [expiration-msecs]} (:bearer-token-claims request)]
    (the nat-int? expiration-msecs)
    (<= (System/currentTimeMillis) (long expiration-msecs))))

(def ^:private not-authenticated-response
  (-> "Not authenticated with bearer token" ring-response/response (ring-response/status 401)))

(defn wrap-authenticated [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)

      not-authenticated-response)))
