(ns telsos.core.algorithms.bags)

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn bag-conj
  ([] {})
  ([bag] bag)
  ([bag x] (update bag x (fnil inc 0)))
  ([bag x & xs]
   (if xs
     (recur (bag-conj bag x) (first xs) (next xs))
     (bag-conj bag x))))

(defn bag-disj
  ([bag] bag)
  ([bag x]
   (when bag
     (if-let [n (get bag x)]
       (let  [n  (long    n)
              n' (dec     n)]
         (if (zero? n')
           (dissoc bag x)
           (assoc  bag x n')))
       bag)))

  ([bag x & xs]
   (when bag
     (let [bag' (bag-disj bag x)]
       (if xs
         (recur bag' (first xs) (next xs))
         bag')))))

(defn bag-count
  ([bag]
   (reduce + (vals bag)))

  ([bag x]
   (get bag x 0)))

(defn bag-union
  [& bags]
  (apply merge-with + bags))

(defn bag-intersection
  "Returns a new bag that is the intersection of two bags.
   Only keeps elements that are present in both bags with the minimum count."
  ([bag]  bag)
  ([bag-1 bag-2]
   (if (< (count bag-2) (count bag-1))
     (recur bag-2 bag-1)

     (into {} (for [[k v1] bag-1
                    :let   [v2 (get bag-2 k)]
                    :when  v2]
                [k (min (long v1) (long v2))]))))

  ([bag-1 bag-2 & bags]
   (if bags
     (recur (bag-intersection bag-1 bag-2) (first bags) (next bags))
     (bag-intersection bag-1 bag-2))))

(defn bag-difference
  "Returns a new bag that is the difference between two bags.
   Only keeps elements that are present in the first bag but not in the second,
   or where the count in the first bag exceeds that in the second."
  ([bag]  bag)
  ([bag-1 bag-2]
   (into {} (for [[k v1] bag-1
                  :let   [v2 (get bag-2 k 0)]
                  :when  (> (long v1) (long v2))]
              [k (- (long v1) (long v2))])))

  ([bag-1 bag-2 & bags]
   (reduce bag-difference bag-1 (conj bags bag-2))))
