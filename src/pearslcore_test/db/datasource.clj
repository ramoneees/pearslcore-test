(ns pearslcore-test.db.datasource
  "Database connection management with HikariCP"
  (:require [clojure.tools.logging :as log]
            [next.jdbc.connection :as connection])
  (:import [com.zaxxer.hikari HikariDataSource]))

(defn create-datasource
  "Create a HikariCP datasource from configuration"
  [config]
  (let [db-spec (merge (:db config)
                       {:jdbcUrl (str "jdbc:sqlite:" (get-in config [:db :subname]))})
        pool-opts (get-in config [:db :pool] {:maximum-pool-size 4
                                              :minimum-idle 2})
        opts (merge {:auto-commit true
                     :read-only false}
                    pool-opts)
        full-spec (merge db-spec opts)]
    (log/info "Creating datasource with pool size:" (:maximum-pool-size opts))
    (connection/->pool HikariDataSource full-spec)))

(defn close-datasource
  "Close a HikariCP datasource"
  [^HikariDataSource ds]
  (when ds
    (.close ds)))