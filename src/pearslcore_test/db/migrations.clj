(ns pearslcore-test.db.migrations
  "Database migrations using Migratus"
  (:require [migratus.core :as migratus]
            [clojure.tools.logging :as log]))

(defn- migratus-config
  "Build Migratus configuration from datasource"
  [ds]
  {:store :database
   :migration-dir "migrations/"
   :db {:datasource ds}})

(defn migrate!
  "Run all pending migrations"
  [ds]
  (log/info "Running migrations...")
  (migratus/migrate (migratus-config ds)))

(defn rollback!
  "Rollback the last migration"
  [ds]
  (log/info "Rolling back last migration...")
  (migratus/rollback (migratus-config ds)))

(defn reset-migrations!
  "Reset the database by rolling back all migrations and re-running them"
  [ds]
  (log/info "Resetting database...")
  (migratus/reset (migratus-config ds)))
