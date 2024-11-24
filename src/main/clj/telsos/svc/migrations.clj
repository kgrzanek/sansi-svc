(ns telsos.svc.migrations
  (:require
   [clojure.java.io :as io]
   [migratus.core :as migratus]
   [telsos.lib.assertions :refer [the]]
   [telsos.lib.logging :as log]
   [telsos.svc.jdbc :as jdbc]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:private migrations-dir "migrations/")

;; GENERATING NEW MIGRATION FILE
(defn create!
  [name- & [type-]]
  (migratus/create {:migration-dir migrations-dir} name- type-))

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

(defn- file->opt-id
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
   (read-ids (str "resources/" migrations-dir)))

  ([dir]
   (-> (let [dir-file (io/file dir)]
         (assert (.isDirectory dir-file))
         (for [file  (file-seq dir-file)
               :let  [id (file->opt-id file)]
               :when (some? id)]

           id))

       sort distinct vec)))

;; DATABASE MIGRATIONS
(defn up-with-datasource!
  [datasource ids]
  (the jdbc/datasource? datasource)
  (let [config
        {:store         :database
         :migration-dir migrations-dir
         :db            {:datasource datasource}}

        ids (->ids ids)]

    (apply migratus/up config ids)
    (log/info (str "Applied migratus/up to ids " ids " with " datasource))))

(defn down-with-datasource!
  [datasource ids]
  (the jdbc/datasource? datasource)
  (let [config
        {:store         :database
         :migration-dir migrations-dir
         :db            {:datasource datasource}}

        ids (->ids ids)]

    (apply migratus/down config ids)
    (log/info (str "Applied migratus/down to ids " ids " with " datasource))))

;; TESTING FIXTURES
(defn fixture
  ([datasource]
   (fixture datasource {}))

  ([datasource {:keys [only-up?]}]
   (the jdbc/datasource? datasource)
   (let [ids (read-ids)]
     (fn [f]
       (up-with-datasource! datasource ids)
       (f)
       (when-not only-up?
         (down-with-datasource! datasource ids))))))
