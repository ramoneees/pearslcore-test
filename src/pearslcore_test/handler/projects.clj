(ns pearslcore-test.handler.projects
  "Project resource handlers"
  (:require [ring.util.response :as response]
            [ring.util.http-response :as http]
            [pearslcore-test.db.repository :as repo]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import [java.util UUID]))

(defn error-response
  [status code message details]
  (let [body {:error {:code code
                      :message message}}]
    {:status status
     :body (if details
             (assoc-in body [:error :details] details)
             body)
     :headers {"Content-Type" "application/json"}}))

(defn bad-request-error
  [message details]
  (error-response 400 "bad_request" message details))

(defn not-found-error
  [message]
  (error-response 404 "not_found" message nil))

(defn conflict-error
  [message details]
  (error-response 409 "conflict" message details))

(defn list-projects
  [ds request]
  (try
    (let [{:keys [limit offset sort order status]} (get-in request [:parameters :query])
          result (repo/list-projects ds {:limit (or limit 20)
                                         :offset (or offset 0)
                                         :sort (or sort "created_at")
                                         :order (or order "desc")
                                         :status status})]
      (http/ok result))
    (catch Exception e
      (log/error e "Error listing projects")
      (error-response 500 "internal_error" "An unexpected error occurred" nil))))

(defn create-project
  "Handle POST /projects. Body is already validated/coerced against the
   closed Malli schema by reitit middleware; here we only normalize the name,
   persist, and translate a unique-constraint conflict into a 409."
  [ds request]
  (try
    (let [body (get-in request [:parameters :body])
          project (repo/create-project! ds {:name (str/trim (:name body))
                                            :status (or (:status body) "planned")})]
      (-> (response/response project)
          (response/status 201)
          (response/header "Location" (str "/projects/" (:id project)))))
    (catch clojure.lang.ExceptionInfo e
      (if (= :duplicate_name (:code (ex-data e)))
        (conflict-error "A project with this name already exists"
                        [{:field "name"
                          :message "must be unique (case-insensitive)"}])
        (throw e)))
    (catch Exception e
      (log/error e "Error creating project")
      (error-response 500 "internal_error" "An unexpected error occurred" nil))))

(defn get-project
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
      (error-response 500 "internal_error" "An unexpected error occurred" nil))))
