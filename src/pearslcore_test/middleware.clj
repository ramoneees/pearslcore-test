(ns pearslcore-test.middleware
  "Ring middleware for the Projects API"
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]))

;; Version header middleware
(defn wrap-version
  "Middleware to handle X-API-Version header"
  [handler config]
  (fn [request]
    (let [version-header (get-in request [:headers "x-api-version"])
          supported-versions (get-in config [:api :supported-versions] #{1})]
      (cond
        ;; No version header - use default
        (nil? version-header)
        (handler request)

        ;; Version header present - validate it
        :else
        (let [version (try
                        (Integer/parseInt version-header)
                        (catch NumberFormatException _
                          nil))]
          (cond
            ;; Invalid version format
            (nil? version)
            {:status 400
             :body {:error {:code "bad_request"
                            :message "Invalid API version format"
                            :details [{:field "X-API-Version"
                                       :message "must be an integer"}]}}
             :headers {"Content-Type" "application/json"}}

            ;; Unsupported version
            (not (contains? supported-versions version))
            {:status 400
             :body {:error {:code "bad_request"
                            :message (str "Unsupported API version: " version)
                            :details [{:field "X-API-Version"
                                       :message (str "supported versions: " (str/join ", " (sort supported-versions)))}]}}
             :headers {"Content-Type" "application/json"}}

            ;; Valid version - proceed
            :else
            (handler request)))))))

;; Error handling middleware
(defn wrap-error
  "Middleware to catch exceptions and return standardized error responses"
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (log/error e "Application error")
          {:status (or (:status data) 500)
           :body {:error {:code (or (:code data) "internal_error")
                          :message (or (:message data) "An unexpected error occurred")}}
           :headers {"Content-Type" "application/json"}}))
      (catch Exception e
        (log/error e "Unexpected error")
        {:status 500
         :body {:error {:code "internal_error"
                        :message "An unexpected error occurred"}}
         :headers {"Content-Type" "application/json"}}))))

;; Request logging middleware
(defn wrap-logging
  "Middleware to log HTTP requests"
  [handler]
  (fn [request]
    (let [start-time (System/currentTimeMillis)
          method (:request-method request)
          uri (:uri request)
          query-string (:query-string request)
          uri-with-query (if query-string
                           (str uri "?" query-string)
                           uri)]
      (log/info (str (-> method name str/upper-case) " " uri-with-query))
      (let [response (handler request)
            elapsed (- (System/currentTimeMillis) start-time)]
        (log/info (str (-> method name str/upper-case) " " uri-with-query
                       " - " (:status response) " - " elapsed "ms"))
        response))))

;; JSON parsing middleware (for malformed JSON)
(defn wrap-json-parse-error
  "Middleware to handle JSON parsing errors"
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (if (= :muuntaja/decode (:type (ex-data e)))
          {:status 400
           :body {:error {:code "bad_request"
                          :message "Malformed JSON in request body"
                          :details [{:field "body"
                                     :message "must be valid JSON"}]}}
           :headers {"Content-Type" "application/json"}}
          (throw e)))
      (catch com.fasterxml.jackson.core.JsonParseException _
        {:status 400
         :body {:error {:code "bad_request"
                        :message "Malformed JSON in request body"
                        :details [{:field "body"
                                   :message "must be valid JSON"}]}}
         :headers {"Content-Type" "application/json"}})
      (catch Exception e
        (let [cause (.getCause e)]
          (if (instance? com.fasterxml.jackson.core.JsonParseException cause)
            {:status 400
             :body {:error {:code "bad_request"
                            :message "Malformed JSON in request body"
                            :details [{:field "body"
                                       :message "must be valid JSON"}]}}
             :headers {"Content-Type" "application/json"}}
            (throw e)))))))
