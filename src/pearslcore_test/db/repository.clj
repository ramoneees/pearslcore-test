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

(defn- now-iso8601 []
  (.format timestamp-formatter (Instant/now)))

(defn- generate-uuid []
  (str (UUID/randomUUID)))

(defn- row->project [row]
  (select-keys row [:id :name :status :created_at]))

(defn- build-where-clause
  [{:keys [status]}]
  (when status
    ["status = ?" (name status)]))

(defn- build-order-by-clause
  [sort order]
  (let [sort-col (case sort
                   "name" "name"
                   "created_at")
        order-dir (if (= order "asc") "ASC" "DESC")]
    (str sort-col " " order-dir ", id ASC")))

(defn create-project!
  "Insert a new project. The unique index on lower(trim(name)) is the
   single source of truth for duplicate detection; a UNIQUE constraint
   violation is translated into a :duplicate_name ex-info."
  [ds project-data]
  (let [params {:id (generate-uuid)
                :name (:name project-data)
                :status (or (:status project-data) "planned")
                :created_at (now-iso8601)}]
    (try
      (sql/insert! ds :projects params)
      (row->project params)
      (catch SQLException e
        (if (str/includes? (.getMessage e) "UNIQUE constraint failed")
          (throw (ex-info "duplicate-name"
                          {:code :duplicate_name
                           :message "A project with this name already exists"}))
          (throw e))))))

(defn get-project-by-id
  [ds id]
  (when-let [row (sql/get-by-id ds :projects id {:builder-fn rs/as-unqualified-lower-maps})]
    (row->project row)))

(defn- count-projects
  [ds filters]
  (let [[where-clause & params] (or (build-where-clause filters) ["1=1"])
        sql-str (str "SELECT COUNT(*) as total FROM projects WHERE " where-clause)
        result (jdbc/execute! ds (into [sql-str] params)
                              {:builder-fn rs/as-unqualified-lower-maps})]
    (:total (first result))))

(defn list-projects
  [ds {:keys [limit offset sort order status]
       :or {limit 20 offset 0 sort "created_at" order "desc"}}]
  (let [filters (when status {:status status})
        total (count-projects ds filters)
        [where-clause & params] (or (build-where-clause filters) ["1=1"])
        order-by (build-order-by-clause sort order)
        sql-str (str "SELECT * FROM projects WHERE " where-clause
                     " ORDER BY " order-by
                     " LIMIT ? OFFSET ?")
        rows (jdbc/execute! ds (into [sql-str] (concat params [limit offset]))
                            {:builder-fn rs/as-unqualified-lower-maps})]
    {:items (mapv row->project rows)
     :limit limit
     :offset offset
     :total total}))

(defn delete-all-projects!
  "Delete all projects. Used in tests."
  [ds]
  (sql/delete! ds :projects [true]))
