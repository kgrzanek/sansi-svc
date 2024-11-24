(ns build
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.tools.build.api :as build-api]))

(def lib     'kongra/telsos-svc)
(def basis   (build-api/create-basis {:project "deps.edn"}))
(def version (format "0.1.%s" (build-api/git-count-revs nil)))

;; SRC/RESOURCES
(def src-dirs           ["src/"])
(def resources-dirs     ["resources/"])
(def src+resources-dirs ["src/" "resources/"])

;; TARGET(S)
(def target-dir  "target/")
(def classes-dir "target/classes/")

(def jar-file     (format "target/%s-%s.jar"            (name lib) version))
(def uberjar-file (format "target/%s-%s-STANDALONE.jar" (name lib) version))

(def main "telsos.svc.core")

;; TASKS
(defn- prs [x] (with-out-str (pr x)))

(defn clean [_]
  (println "deleting" (prs target-dir))
  (time (build-api/delete {:path target-dir})))

(defn compile-clj [_]
  (println "compiling" (prs src-dirs) "into" (prs classes-dir))
  (time
    (build-api/compile-clj
      {:basis     basis
       :src-dirs  src-dirs
       :java-opts ["-XX:+UseStringDeduplication"
                   "-Dclojure.compiler.direct-linking=true"
                   "-Dclojure.warn.on.reflection=false"
                   "-Dclojure.assert=true"]

       :class-dir classes-dir})))

(defn uberjar [_]
  (clean       nil)
  (compile-clj nil)

  (println "writing pom for" lib version)
  (time
    (build-api/write-pom
      {:class-dir classes-dir
       :lib       lib
       :version   version
       :basis     basis
       :src-dirs  src-dirs}))

  (println "copying" (prs src+resources-dirs) "to" (prs classes-dir))
  (time
    (build-api/copy-dir
      {:src-dirs   src+resources-dirs
       :target-dir classes-dir}))

  (println "creating" uberjar-file)
  (time
    (build-api/uber
      {:basis     basis
       :class-dir classes-dir
       :main      main
       :uber-file uberjar-file})))
