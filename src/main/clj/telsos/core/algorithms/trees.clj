(ns telsos.core.algorithms.trees
  (:require
   [telsos.core.algorithms.paip :as paip]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; EXTRACTION OF PATHS FROM THE TREES
(defrecord ^:private TreePathIncomplete [nodes])
(defrecord ^:private TreePathComplete   [nodes])

(defrecord TreeEntry
  [parent-node index-or-key node])

(defn- edn-path-adjs
  [{:keys [nodes] :as path} adjs]
  (when (instance? TreePathIncomplete path)
    (if-let [adjs (seq (adjs (first nodes)))]
      (map #(->TreePathIncomplete (cons % nodes)) adjs)

      [(->TreePathComplete nodes)])))

(defn- edn-path->associative-keys
  [{:keys [nodes]} repr]
  (->> nodes
       (map (fn [node]
              (or (when (instance? TreeEntry node) (:index-or-key node))
                  (repr node)
                  (str  node))))
       (reverse)
       (vec)))

(defn edn->associative-paths
  [e repr]
  (->> (paip/breadth-first-tree-seq (->TreePathIncomplete (list e)) edn-path-adjs)
       (filter #(instance? TreePathComplete %))
       (map    #(edn-path->associative-keys % repr))))
