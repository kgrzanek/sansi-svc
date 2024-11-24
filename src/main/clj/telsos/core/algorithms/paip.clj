(ns telsos.core.algorithms.paip)

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

;; TREE SEARCH ROUTINES FROM BY PAIP, CHAPTER 6.4

(defn flip-2 [f]
  (fn [x y] (f y x)))

(defn lazy-cat-2 [xs ys]
  (lazy-cat xs ys))

(def breadth-first-combiner                concat)
(def lazy-breadth-first-combiner       lazy-cat-2)
(def depth-first-combiner      (flip-2     concat))
(def lazy-depth-first-combiner (flip-2 lazy-cat-2))

(defn tree-search
  [start goal-fn adjs comb]
  (loop [nodes (list start)]
    (when (seq nodes)
      (let [obj (first nodes)]
        (if (goal-fn obj)
          obj
          (recur (comb (rest nodes) (adjs obj))))))))

(defn breadth-first-search
  [start goal-fn adjs]
  (tree-search start goal-fn adjs breadth-first-combiner))

(defn depth-first-search
  [start goal-fn adjs]
  (tree-search start goal-fn adjs depth-first-combiner))

(defn breadth-first-tree-levels
  [start adjs]
  (->>
    (list              start)
    (iterate #(mapcat adjs %))
    (take-while          seq)))

(defn breadth-first-tree-seq
  ([start adjs]
   (apply concat (breadth-first-tree-levels start adjs)))

  ([start adjs depth]
   (->>
     (breadth-first-tree-levels start adjs)
     (take   depth)
     (apply concat))))
