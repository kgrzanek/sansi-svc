(ns telsos.svc.migrations
  (:require
   [clojure.java.io]
   [migratus.core]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.logging :as log]
   [telsos.lib.strings]
   [telsos.svc.jdbc :refer [datasource?]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private migrations-dir "resources/migrations/")

;; GENERATING NEW MIGRATION FILE
(defn create!
  [name & [type]]
  (the telsos.lib.strings/non-blank? name)
  (migratus.core/create {:migration-dir migrations-dir} name type))

;; IDS
(defn- ->id
  ^long [x]
  (let [id (if (sequential? x) (first x) x)]
    (cond
      (int?    id) (long           id)
      (string? id) (Long/parseLong id)

      :else
      (throw (ex-info "Illegal migration id" {:id x})))))

(defn- ->ids
  [xs]
  (->> xs (map ->id) distinct sort))

(defn- file->maybe-id
  ^Long [^java.io.File file]
  (when (.isFile file)
    (let [file-name (.getName file)]
      ;; In names like 20240708172739-create-test1-table.up.sql, the id takes
      ;; the initial 14 digits.
      (when (>= (.length file-name) 14)
        (telsos.lib.strings/parse-long (.substring file-name 0 14))))))

(defn- read-ids
  ([]
   (read-ids migrations-dir))

  ([dir]
   (-> (let [dir-file (clojure.java.io/file dir)]
         (when-not (.isDirectory dir-file)
           (throw (ex-info "Not a directory" {:dir dir})))

         (for [file  (file-seq dir-file)
               :let  [id (file->maybe-id file)]
               :when (some? id)]

           id))

       sort distinct vec)))

;; DATABASE MIGRATIONS
(defn- up-with-datasource!
  [datasource ids]
  (the datasource? datasource)
  (let [config
        {:store         :database
         :migration-dir migrations-dir
         :db            {:datasource datasource}}

        ids (->ids ids)]

    (apply migratus.core/up config ids)
    (log/info (str "Applied migratus/up to ids " ids " with " datasource))))

(defn- down-with-datasource!
  [datasource ids]
  (the datasource? datasource)
  (let [config
        {:store         :database
         :migration-dir migrations-dir
         :db            {:datasource datasource}}

        ids (->ids ids)]

    (apply migratus.core/down config ids)
    (log/info (str "Applied migratus/down to ids " ids " with " datasource))))

;; TESTING FIXTURES
(defn fixture
  ([datasource]
   (fixture datasource {}))

  ([datasource {:keys [only-up?]}]
   (the datasource? datasource)
   (let [ids (read-ids)]
     (fn [f]
       (up-with-datasource! datasource ids)
       (f)
       (when-not only-up?
         (down-with-datasource! datasource ids))))))
