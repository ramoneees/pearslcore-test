(ns pearslcore-test.db.repository
  "Database repository for projects"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str])
  (:import [java.sql SQLException]
           [java.util UUID]
           [java.time Instant]
           [java.time.format DateTimeFormatter]))

(def ^:private timestamp-formatter DateTimeFormatter/ISO_INSTANT)

(defn- now-iso8601
  "Get current timestamp in ISO-8601 format"
  []
  (.format timestamp-formatter (Instant/now)))

(defn- generate-uuid
  "Generate a random UUID string"
  []
  (str (UUID/randomUUID)))

;; Row transformation
(defn- row->project
  "Transform a database row to project map"
  [row]
  (select-keys row [:id :name :status :created_at]))

;; SQL builders
(defn- build-where-clause
  "Build WHERE clause for filters"
  [filters]
  (when-let [status (:status filters)]
    ["status = ?" (name status)]))

(defn- build-order-by-clause
  "Build ORDER BY clause"
  [sort order]
  (let [sort-col (case sort
                   "created_at" "created_at"
                   "name" "name"
                   "created_at")
        order-dir (if (= order "asc") "ASC" "DESC")]
    (str sort-col " " order-dir ", id ASC")))

(declare get-project-by-name)

;; Repository functions
(defn create-project!
  "Create a new project. Returns the created project or throws on conflict."
  [ds project-data]
  (let [existing (get-project-by-name ds (:name project-data))
        id (generate-uuid)
        created-at (now-iso8601)
        status (or (:status project-data) "planned")
        params {:id id
                :name (:name project-data)
                :status status
                :created_at created-at}]
    (try
      (when existing
        (throw (ex-info "duplicate-name"
                        {:code :duplicate_name
                         :message "A project with this name already exists"})))
      (sql/insert! ds :projects params {:return-keys true})
      (row->project {:id id
                     :name (:name params)
                     :status status
                     :created_at created-at})
      (catch SQLException e
        (if (str/includes? (.getMessage e) "UNIQUE constraint failed")
          (throw (ex-info "duplicate-name"
                          {:code :duplicate_name
                           :message "A project with this name already exists"}))
          (throw e))))))

(defn get-project-by-id
  "Get a project by ID. Returns nil if not found."
  [ds id]
  (when-let [row (sql/get-by-id ds :projects id {:builder-fn rs/as-unqualified-lower-maps})]
    (row->project row)))

(defn get-project-by-name
  "Get a project by name (case-insensitive after trim). Returns nil if not found."
  [ds name]
  (let [normalized (str/lower-case (str/trim name))
        rows (jdbc/execute! ds
                            ["SELECT * FROM projects WHERE lower(trim(name)) = ?" normalized]
                            {:builder-fn rs/as-unqualified-lower-maps})]
    (when (seq rows)
      (row->project (first rows)))))

(defn- count-projects
  "Count total projects matching filters"
  [ds filters]
  (let [[where-clause & params] (or (build-where-clause filters) ["1=1"])
        sql-str (str "SELECT COUNT(*) as total FROM projects WHERE " where-clause)
        result (jdbc/execute! ds (into [sql-str] params)
                              {:builder-fn rs/as-unqualified-lower-maps})]
    (:total (first result))))

(defn list-projects
  "List projects with pagination, sorting, and filtering"
  [ds {:keys [limit offset sort order status] :or {limit 20 offset 0 sort "created_at" order "desc"}}]
  (let [filters (when status {:status status})
        total (count-projects ds filters)
        [where-clause & params] (or (build-where-clause filters) ["1=1"])
        order-by (build-order-by-clause sort order)
        sql-str (str "SELECT * FROM projects WHERE " where-clause
                     " ORDER BY " order-by
                     " LIMIT ? OFFSET ?")
        params-with-paging (into [sql-str]
                                 (concat params [limit offset]))
        rows (jdbc/execute! ds params-with-paging
                            {:builder-fn rs/as-unqualified-lower-maps})]
    {:items (mapv row->project rows)
     :limit limit
     :offset offset
     :total total}))

(defn delete-all-projects!
  "Delete all projects. Used in tests."
  [ds]
  (sql/delete! ds :projects [true]))
