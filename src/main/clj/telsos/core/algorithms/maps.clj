(ns telsos.core.algorithms.maps)

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn invert-map->multimap
  [m]
  (loop [result  (transient {})
         entries (seq m)]
    (if-not entries
      (persistent! result)

      (let [e (first entries)
            k (key e)
            v (val e)]

        (recur (if-let [s (result v)]
                 (assoc! result v (conj s k))
                 (assoc! result v #{k})) (next entries))))))
