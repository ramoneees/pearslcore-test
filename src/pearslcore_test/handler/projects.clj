(ns pearslcore-test.handler.projects
  "Project resource handlers"
  (:require [ring.util.response :as response]
            [ring.util.http-response :as http]
            [pearslcore-test.db.repository :as repo]
            [pearslcore-test.schema :as schema]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import [java.util UUID]))

;; Error response helpers
(defn error-response
  "Create a standardized error response"
  [status code message details]
  (let [body {:error {:code code
                      :message message}}]
    {:status status
     :body (if details
             (assoc-in body [:error :details] details)
             body)
     :headers {"Content-Type" "application/json"}}))

(defn validation-error
  "Create a 422 validation error response"
  [details]
  (error-response 422
                  "validation_error"
                  "Request validation failed"
                  details))

(defn bad-request-error
  "Create a 400 bad request error response"
  [message details]
  (error-response 400
                  "bad_request"
                  message
                  details))

(defn not-found-error
  "Create a 404 not found error response"
  [message]
  (error-response 404
                  "not_found"
                  message
                  nil))

(defn conflict-error
  "Create a 409 conflict error response"
  [message details]
  (error-response 409
                  "conflict"
                  message
                  details))

;; Project handlers
(defn list-projects
  "Handle GET /projects request"
  [ds request]
  (try
    (let [{:keys [limit offset sort order status]} (get-in request [:parameters :query])
          result (repo/list-projects ds {:limit (or limit 20)
                                         :offset (or offset 0)
                                         :sort (or sort "created_at")
                                         :order (or order "desc")
                                         :status (some-> status keyword)})]
      (http/ok result))
    (catch Exception e
      (log/error e "Error listing projects")
      (error-response 500
                      "internal_error"
                      "An unexpected error occurred"
                      nil))))

(defn- validate-create-request
  "Validate a project creation request. Returns {:keys [valid? errors data]}"
  [body]
  (cond
    (nil? body)
    {:valid? false
     :errors [{:field "body" :message "Request body is required"}]}

    (not (map? body))
    {:valid? false
     :errors [{:field "body" :message "Request body must be a JSON object"}]}

    (not (contains? body :name))
    {:valid? false
     :errors [{:field "name" :message "is required"}]}

    :else
    (let [errors []
          name-validation (schema/validate-project-name (:name body))
          errors (if (:valid? name-validation)
                   errors
                   (conj errors {:field "name"
                                 :message (:error name-validation)}))
          errors (if (and (contains? body :status)
                          (not (#{:planned :active :completed :archived "planned" "active" "completed" "archived"}
                                (:status body))))
                   (conj errors {:field "status"
                                 :message "must be one of: planned, active, completed, archived"})
                   errors)
          errors (if (contains? body :id)
                   (conj errors {:field "id"
                                 :message "cannot be specified"})
                   errors)
          errors (if (contains? body :created_at)
                   (conj errors {:field "created_at"
                                 :message "cannot be specified"})
                   errors)
          allowed-fields #{:name :status}
          unknown-fields (remove allowed-fields (keys body))
          errors (if (seq unknown-fields)
                   (conj errors {:field (-> unknown-fields first name)
                                 :message "is not allowed"})
                   errors)]
        (if (seq errors)
          {:valid? false :errors errors}
          {:valid? true
           :data {:name (str/trim (:name body))
                  :status (or (when-let [s (:status body)]
                                (if (keyword? s) s (keyword s)))
                              :planned)}}))))

(defn create-project
  "Handle POST /projects request"
  [ds {:keys [body-params]}]
  (try
    (let [validation (validate-create-request body-params)]
      (if-not (:valid? validation)
        (validation-error (:errors validation))
        (let [project-data (:data validation)]
          (try
            (let [project (repo/create-project! ds {:name (:name project-data)
                                                    :status (name (:status project-data))})
                  id (:id project)]
              (-> (response/response project)
                  (response/status 201)
                  (response/header "Location" (str "/projects/" id))))
            (catch clojure.lang.ExceptionInfo e
              (if (= :duplicate_name (:code (ex-data e)))
                (conflict-error "A project with this name already exists"
                                [{:field "name"
                                  :message "must be unique (case-insensitive)"}])
                (throw e)))
            (catch Exception e
              (log/error e "Error creating project")
              (error-response 500
                              "internal_error"
                              "An unexpected error occurred"
                              nil))))))
    (catch Exception e
      (log/error e "Error in create-project handler")
      (error-response 500
                      "internal_error"
                      "An unexpected error occurred"
                      nil))))

(defn get-project
  "Handle GET /projects/:id request"
  [ds request]
  (try
    (let [id (get-in request [:parameters :path :id])]
      (if-not (try
                (UUID/fromString id)
                true
                (catch IllegalArgumentException _ false))
        (bad-request-error "Invalid UUID format"
                           [{:field "id"
                             :message "must be a valid UUID"}])
        (if-let [project (repo/get-project-by-id ds id)]
          (http/ok project)
          (not-found-error "Project not found"))))
    (catch Exception e
      (log/error e "Error getting project")
      (error-response 500
                      "internal_error"
                      "An unexpected error occurred"
                      nil))))
