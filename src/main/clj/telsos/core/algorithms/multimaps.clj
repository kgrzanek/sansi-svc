(ns telsos.core.algorithms.multimaps
  (:require
   [clojure.set :as set]
   [telsos.core.algorithms.bags :refer [bag-conj bag-disj bag-union]]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defprotocol MultimapKind
  (multimap-empty       [this])
  (multimap-conj        [this])
  (multimap-disj        [this])
  (multimap-vals-seq    [this])
  (multimap-vals-union  [this])
  (multimap-colls-union [this]))

(def multimap
  (reify MultimapKind
    (multimap-empty       [_this]       #{})
    (multimap-conj        [_this]      conj)
    (multimap-disj        [_this]      disj)
    (multimap-vals-seq    [_this]       seq)
    (multimap-vals-union  [_this] set/union)
    (multimap-colls-union [_this] set/union)))

(def bag-multimap
  (reify MultimapKind
    (multimap-empty       [_this]        {})
    (multimap-conj        [_this]  bag-conj)
    (multimap-disj        [_this]  bag-disj)
    (multimap-vals-seq    [_this]      keys)
    (multimap-vals-union  [_this] set/union)
    (multimap-colls-union [_this] bag-union)))

(defn multimap-assoc
  ([multimap-kind m k v]
   (let [coll
         (or (get m k) (multimap-empty multimap-kind))

         updated-coll
         ((multimap-conj multimap-kind) coll v)]

     (assoc m k updated-coll)))

  ([multimap-kind m k v & kvs]
   (let [m (multimap-assoc multimap-kind m k v)]
     (if kvs
       (if (next kvs)
         (recur multimap-kind m (first kvs) (second kvs) (nnext kvs))
         (throw
           (ex-info
             "multimap-assoc expects even number of kvs arguments, odd number found"
             {:kvs kvs})))

       m))))

(defn multimap-dissoc
  ([_multimap-kind m] m)
  ([multimap-kind m k v & vs]
   (let [coll
         (or (get m k) (multimap-empty multimap-kind))

         updated-coll
         (apply (multimap-disj multimap-kind) coll (conj vs v))]

     (if (empty? updated-coll)
       (dissoc m k)
       (assoc  m k updated-coll)))))

(defn multimap-vals
  [multimap-kind m]
  (let [vals-union (multimap-vals-union multimap-kind)
        vals-seq   (multimap-vals-seq   multimap-kind)]

    (apply vals-union (map vals-seq (vals m)))))

(defn multimap-seq
  [multimap-kind m]
  (let [vals-seq (multimap-vals-seq multimap-kind)]
    (mapcat (fn [[k v]] (for [item (vals-seq v)] [k item])) m)))

(defn multimap-merge
  [multimap-kind & maps]
  (apply merge-with (multimap-colls-union multimap-kind) maps))

(defn multimap-contains?
  [multimap-kind m k v]
  (contains? (or (get m k) (multimap-empty multimap-kind)) v))
