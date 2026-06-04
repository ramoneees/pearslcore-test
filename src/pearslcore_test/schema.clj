(ns pearslcore-test.schema
  "Malli schemas for request/response validation and coercion"
  (:require [clojure.string :as str]))

(def ^:private trimmed-name-regex
  "Regex for valid project name after trimming - allows letters, numbers, spaces, and . _ - ' / ( )"
  #"[a-zA-Z0-9\s\.\_\-\'\/\(\)]+")

(def ^:private no-control-chars?
  "Check that string has no control characters"
  (fn [s]
    (not (re-find #"\p{C}" s))))

;; Status enum
(def status-enum
  "Valid project statuses"
  [:enum {:description "Project status"}
   "planned" "active" "completed" "archived"])

;; Project name schema
(def project-name-schema
  "Schema for project name validation"
  [:and {:description "Project name"}
   string?
   [:fn {:error/message "must not contain control characters"}
    no-control-chars?]
   [:fn {:error/message "contains invalid characters - only letters, numbers, spaces, and . _ - ' / ( ) are allowed"}
    (fn [s] (re-matches trimmed-name-regex s))]
   [:fn {:error/message "must be between 3 and 120 characters"}
    (fn [s] (let [trimmed (str/trim s)]
              (<= 3 (count trimmed) 120)))]])

;; UUID schema
(def uuid-string
  "Valid UUID string"
  [:re {:description "UUID string"
        :error/message "must be a valid UUID"}
   #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"])

;; ISO-8601 timestamp — optional fractional seconds to match Java's ISO_INSTANT formatter
(def iso-8601-timestamp
  "ISO-8601 / RFC 3339 timestamp"
  [:re {:description "ISO-8601 timestamp"
        :error/message "must be a valid ISO-8601 timestamp"}
   #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$"])

;; Project response schema
(def project-response-schema
  "Schema for project response"
  [:map {:description "Project resource"}
   [:id {:description "Unique identifier"} uuid-string]
   [:name {:description "Project name"} string?]
   [:status {:description "Project status"} status-enum]
   [:created_at {:description "Creation timestamp"} iso-8601-timestamp]])

;; POST request schema (closed)
(def create-project-request-schema
  "Schema for creating a project"
  [:map {:closed true
         :description "Project creation request"}
   [:name {:error/message "is required"} project-name-schema]
   [:status {:optional true
             :error/message "must be one of: planned, active, completed, archived"}
    status-enum]])

;; Project list response schema
(def project-list-response-schema
  "Schema for project list response"
  [:map {:description "Project list response"}
   [:items {:description "List of projects"}
    [:vector project-response-schema]]
   [:limit {:description "Maximum items returned"} [:int {:min 1 :max 100}]]
   [:offset {:description "Offset into total results"} [:int {:min 0}]]
   [:total {:description "Total matching items"} [:int {:min 0}]]])

;; Pagination query params
(def pagination-params
  "Schema for pagination query parameters"
  [:map {:closed false}
   [:limit {:optional true} [:int {:min 1 :max 100}]]
   [:offset {:optional true} [:int {:min 0}]]])

;; Sorting query params
(def sorting-params
  "Schema for sorting query parameters"
  [:map {:closed false}
   [:sort {:optional true} [:enum "created_at" "name"]]
   [:order {:optional true} [:enum "asc" "desc"]]])

;; Status filter
(def status-filter-params
  "Schema for status filter parameter"
  [:map {:closed false}
   [:status {:optional true} status-enum]])

;; Combined list params
(def list-projects-params-schema
  "Schema for GET /projects query parameters"
  [:merge pagination-params sorting-params status-filter-params])

;; Error response schema
(def error-detail-schema
  "Schema for error detail"
  [:map {:description "Error detail"}
   [:field {:description "Field that caused the error"} string?]
   [:message {:description "Error message"} string?]])

(def error-response-schema
  "Schema for error response"
  [:map {:description "Error response"}
   [:error [:map
            [:code {:description "Error code"} string?]
            [:message {:description "Error message"} string?]
            [:details {:optional true
                       :description "List of error details"}
             [:vector error-detail-schema]]]]])

;; Validation helpers
(defn validate-project-name
  "Validate project name and return normalized form or error details"
  [name]
  (let [trimmed (str/trim name)]
    (cond
      (str/blank? name)
      {:valid? false :error "is required"}

      (< (count trimmed) 3)
      {:valid? false :error "must be between 3 and 120 characters"}

      (> (count trimmed) 120)
      {:valid? false :error "must be between 3 and 120 characters"}

      (not (no-control-chars? trimmed))
      {:valid? false :error "must not contain control characters"}

      (not (re-matches trimmed-name-regex trimmed))
      {:valid? false :error "contains invalid characters - only letters, numbers, spaces, and . _ - ' / ( ) are allowed"}

      :else
      {:valid? true :value trimmed})))

