(ns pearslcore-test.config
  "Application configuration management"
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]))

(def defaults
  {:http {:port 3000}
   :db {:classname "org.sqlite.JDBC"
        :subprotocol "sqlite"
        :subname ".data/dev.db"
        :pool {:maximum-pool-size 4
               :minimum-idle 2}}
   :migrations {:store :database
                :migration-dir "migrations"}
   :api {:default-version 1
         :supported-versions #{1}}})

(defn- deep-merge
  "Recursively merge maps"
  [a b]
  (if (and (map? a) (map? b))
    (merge-with deep-merge a b)
    b))

(defn load-config
  "Load configuration from EDN file if it exists, otherwise use defaults"
  ([]
   (load-config nil))
  ([config-file]
   (let [cfg (if config-file
               (if (.exists (io/file config-file))
                 (try
                   (-> config-file slurp edn/read-string)
                   (catch Exception e
                     (log/error e "Failed to load config file:" config-file)
                     defaults))
                 defaults)
               defaults)]
     (deep-merge defaults cfg))))

(defn get-config
  "Get the active configuration"
  []
  (load-config (System/getenv "PROJECTS_API_CONFIG")))