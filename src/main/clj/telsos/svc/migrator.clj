(ns telsos.svc.migrator
  (:require
   [clojure.java.io :as io]
   [migratus.core :as migratus]
   [telsos.lib.logging :as log]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private MIGRATIONS-DIR "migrations/")

;; GENERATING NEW MIGRATION FILE
(defn create!
  [name- & [type-]]
  (migratus/create {:migration-dir MIGRATIONS-DIR} name- type-))

;; IDS
(defn- ->id
  [x]
  (let [id (if (sequential? x) (first x) x)]
    (cond
      (int?    id) (long           id)
      (string? id) (Long/parseLong id)

      :else
      (assert false (str "Illegal migration id " x)))))

(defn- ->ids
  [xs]
  (->> xs (map ->id) (distinct) (sort)))

(defn- file->maybe-id
  [^java.io.File file]
  (when (.isFile file)
    (let [file-name (.getName file)]
      ;; In names like 20240708172739-create-test1-table.up.sql, the id takes
      ;; the initial 14 digits.
      (when (>= (.length file-name) 14)
        (try (Long/parseLong (.substring file-name 0 14))
             (catch NumberFormatException _))))))

(defn read-ids
  ([]
   (read-ids (str "resources/" MIGRATIONS-DIR)))

  ([dir]
   (-> (let [dir-file (io/file dir)]
         (assert (.isDirectory dir-file))
         (for [file  (file-seq dir-file)
               :let  [id (file->maybe-id file)]
               :when (some? id)]

           id))

       sort distinct vec)))

;; DATABASE MIGRATIONS
(defn up-with-datasource!
  [datasource ids]
  (let [config
        {:store         :database
         :migration-dir MIGRATIONS-DIR
         :db            {:datasource datasource}}

        ids (->ids ids)]

    (apply migratus/up config ids)
    (log/info (str "Applied migratus/up to ids " ids " with " datasource))))

(defn down-with-datasource!
  [datasource ids]
  (let [config
        {:store         :database
         :migration-dir MIGRATIONS-DIR
         :db            {:datasource datasource}}

        ids (->ids ids)]

    (apply migratus/down config ids)
    (log/info (str "Applied migratus/down to ids " ids " with " datasource))))

;; TESTING FIXTURES
(defn migrations-fixture
  ([datasource]
   (migrations-fixture datasource {}))

  ([datasource {:keys [only-up?]}]
   (let [ids (read-ids)]
     (fn [f]
       (up-with-datasource! datasource ids)
       (f)
       (when-not only-up?
         (down-with-datasource! datasource ids))))))
