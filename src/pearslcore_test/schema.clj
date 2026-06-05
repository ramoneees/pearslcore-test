(ns pearslcore-test.schema
  "Malli schemas for request/response validation and coercion")

(def ^:private trimmed-name-regex
  #"[a-zA-Z0-9\s\.\_\-\'\/\(\)]+")

(def ^:private no-control-chars?
  (fn [s]
    (not (re-find #"\p{C}" s))))

(def status-enum
  "Valid project statuses"
  [:enum {:description "Project status"}
   "planned" "active" "completed" "archived"])

(def project-name-schema
  "Schema for project name validation"
  [:and {:description "Project name"}
   string?
   [:fn {:error/message "must not contain control characters"}
    no-control-chars?]
   [:fn {:error/message "contains invalid characters - only letters, numbers, spaces, and . _ - ' / ( ) are allowed"}
    (fn [s] (re-matches trimmed-name-regex s))]
   [:fn {:error/message "must be between 3 and 120 characters"}
    (fn [s] (let [t (clojure.string/trim s)]
              (<= 3 (count t) 120)))]])

(def uuid-string
  "Valid UUID string"
  [:re {:description "UUID string"
        :error/message "must be a valid UUID"}
   #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"])

(def iso-8601-timestamp
  "ISO-8601 / RFC 3339 timestamp (optional fractional seconds for Instant/now output)"
  [:re {:description "ISO-8601 timestamp"
        :error/message "must be a valid ISO-8601 timestamp"}
   #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$"])

(def project-response-schema
  "Schema for project response"
  [:map {:description "Project resource"}
   [:id {:description "Unique identifier"} uuid-string]
   [:name {:description "Project name"} string?]
   [:status {:description "Project status"} status-enum]
   [:created_at {:description "Creation timestamp"} iso-8601-timestamp]])

(def create-project-request-schema
  "Schema for creating a project (closed: unknown/forbidden fields are rejected)"
  [:map {:closed true
         :description "Project creation request"}
   [:name {:error/message "is required"} project-name-schema]
   [:status {:optional true
             :error/message "must be one of: planned, active, completed, archived"}
    status-enum]])

(def project-list-response-schema
  "Schema for project list response"
  [:map {:description "Project list response"}
   [:items {:description "List of projects"}
    [:vector project-response-schema]]
   [:limit {:description "Maximum items returned"} [:int {:min 1 :max 100}]]
   [:offset {:description "Offset into total results"} [:int {:min 0}]]
   [:total {:description "Total matching items"} [:int {:min 0}]]])

(def list-projects-params-schema
  "Schema for GET /projects query parameters"
  [:map {:closed false}
   [:limit {:optional true} [:int {:min 1 :max 100}]]
   [:offset {:optional true} [:int {:min 0}]]
   [:sort {:optional true} [:enum "created_at" "name"]]
   [:order {:optional true} [:enum "asc" "desc"]]
   [:status {:optional true} status-enum]])

(def error-detail-schema
  [:map {:description "Error detail"}
   [:field {:description "Field that caused the error"} string?]
   [:message {:description "Error message"} string?]])

(def error-response-schema
  [:map {:description "Error response"}
   [:error [:map
            [:code {:description "Error code"} string?]
            [:message {:description "Error message"} string?]
            [:details {:optional true
                       :description "List of error details"}
             [:vector error-detail-schema]]]]])
