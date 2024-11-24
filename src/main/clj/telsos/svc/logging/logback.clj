(ns telsos.svc.logging.logback
  (:import
   (ch.qos.logback.classic Logger LoggerContext)
   (ch.qos.logback.classic.encoder PatternLayoutEncoder)
   (ch.qos.logback.core Appender)
   (ch.qos.logback.core.rolling RollingFileAppender TimeBasedRollingPolicy)
   (org.slf4j LoggerFactory)))

(set! *warn-on-reflection*       true)
(set! *unchecked-math* :warn-on-boxed)

(defn configure-FILE-appender!
  [log-name]
  (let [log-name (name log-name)
        context  ^LoggerContext (LoggerFactory/getILoggerFactory)
        root     (.getLogger context Logger/ROOT_LOGGER_NAME)

        ;; Create pattern encoder
        encoder
        (doto (PatternLayoutEncoder.)
          (.setContext context)
          (.setPattern "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n")
          (.start))

        ;; Create rolling policy
        rolling-policy
        (doto (TimeBasedRollingPolicy.)
          (.setContext context)
          (.setFileNamePattern (format "logs/application-%s.%%d{yyyy-MM-dd}.log" log-name))
          (.setMaxHistory 30))

        ;; Create file appender
        file-appender
        (doto (RollingFileAppender.)
          (.setContext context)
          (.setName "FILE")
          (.setFile (format "logs/application-%s.log" log-name))
          (.setEncoder encoder)
          (.setRollingPolicy rolling-policy))]

    (.setParent rolling-policy file-appender)
    (.start rolling-policy)

    (.start file-appender)

    ;; Find and replace the existing FILE appender
    (let [it (.iteratorForAppenders root)]
      (while (.hasNext it)
        (let [app ^Appender (.next it)]
          (when (= (.getName app) "FILE")
            (.detachAppender root app)))))

    ;; Attach new appender
    (.addAppender root file-appender)))
