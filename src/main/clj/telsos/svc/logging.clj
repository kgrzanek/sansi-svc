(ns telsos.svc.logging
  (:require
   [clojure.tools.logging :as log]
   [telsos.lib.binding :as binding]))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(def mdc* (binding/create-scoped))

(defmacro with-mdc
  [mdc & body]
  `(binding/scoped [mdc* ~mdc]
     ~@body))

(defmacro trace
  ([throwable msg] (with-meta `(log/trace @mdc* ~throwable ~msg) (meta &form)))
  ([msg]           (with-meta `(log/trace @mdc*            ~msg) (meta &form))))

(defmacro debug
  ([throwable msg] (with-meta `(log/debug @mdc* ~throwable ~msg) (meta &form)))
  ([msg]           (with-meta `(log/debug @mdc*            ~msg) (meta &form))))

(defmacro info
  ([throwable msg] (with-meta `(log/info @mdc* ~throwable ~msg) (meta &form)))
  ([msg]           (with-meta `(log/info @mdc*            ~msg) (meta &form))))

(defmacro warn
  ([throwable msg] (with-meta `(log/warn @mdc* ~throwable ~msg) (meta &form)))
  ([msg]           (with-meta `(log/warn @mdc*            ~msg) (meta &form))))

(defmacro error
  ([throwable msg] (with-meta `(log/error @mdc* ~throwable ~msg) (meta &form)))
  ([msg]           (with-meta `(log/error @mdc*            ~msg) (meta &form))))

(defmacro fatal
  ([throwable msg] (with-meta `(log/fatal @mdc* ~throwable ~msg) (meta &form)))
  ([msg]           (with-meta `(log/fatal @mdc*            ~msg) (meta &form))))
