(ns telsos.core.algorithms.seqs)

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn mark-last
  "Takes (e0 e1 ... en) and returns (false false ... true) or (false false ...)
  if the argument is infinite."
  [xs]
  (if-not (seq xs)
    '()
    (let [[_ & others] xs]
      (if-not (seq others)
        '(true)
        (cons false (lazy-seq (mark-last others)))))))

(defn with-predecessors
  ([coll]
   (with-predecessors coll '()))

  ([coll predecessors]
   (lazy-seq
     (when (seq coll)
       (let [e (first coll)]
         (cons [e predecessors]
               (with-predecessors (rest coll) (conj predecessors e))))))))
