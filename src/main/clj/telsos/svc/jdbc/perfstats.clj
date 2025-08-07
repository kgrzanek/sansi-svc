(ns telsos.svc.jdbc.perfstats
  (:require
   [telsos.lib.algorithms.vecs]
   [telsos.lib.assertions :refer [the]])
  (:import
   (telsos.lib PerfStats)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn create-perfstats
  ([n]
   (PerfStats. n))

  ([n & ns-]
   (vec (cons (create-perfstats n) (map create-perfstats ns-)))))

(defn perfstats?
  [perfstats]
  (or (instance? PerfStats perfstats)
      (and (vector?        perfstats)

           (telsos.lib.algorithms.vecs/vec-every?
             #(instance? PerfStats %) perfstats))))

(defn perfstats-update!
  [perfstats ^long start-nanos ^long end-nanos]
  (the perfstats? perfstats)
  (if (instance? PerfStats perfstats)
    (.update ^PerfStats perfstats start-nanos end-nanos)

    (dotimes [i (count perfstats)]
      (perfstats-update! (nth perfstats i) start-nanos end-nanos)))

  perfstats)

(defn perfstats-msecs
  [perfstats]
  (the perfstats? perfstats)
  (if (instance? PerfStats perfstats)
    (let [[n acc avg] (.statsMsecs ^PerfStats perfstats)]
      {:n (long n) :acc-msecs acc :avg-msecs avg})

    (mapv perfstats-msecs perfstats)))

;; FOR THE FUTURE:
#_(when perfstats
    (let [end-nanos (System/nanoTime)]
      (signal-restarting-event
        events-handler {:perfstats-update! [start-nanos end-nanos]})

      (perfstats-update! perfstats start-nanos end-nanos)))
