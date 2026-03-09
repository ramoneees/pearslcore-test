(defproject pearslcore-test "0.1.0-SNAPSHOT"
  :description "Projects API - A production-shaped CRUD service"
  :url "https://github.com/example/projects-api"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 ;; HTTP stack
                 [ring/ring "1.12.2"]
                 [metosin/reitit "0.7.2"]
                 [metosin/muuntaja "0.6.10"]
                 [metosin/ring-http-response "0.9.4"]
                 [ring/ring-jetty-adapter "1.12.2"]
                 ;; Validation & coercion
                 [metosin/malli "0.16.3"]
                 ;; State management
                 [integrant "0.13.1"]
                 ;; Database
                 [com.github.seancorfield/next.jdbc "1.3.939"]
                 [com.zaxxer/HikariCP "6.0.0"]
                 [org.xerial/sqlite-jdbc "3.46.0.0"]
                 ;; Migrations
                 [migratus "1.6.3"]
                 ;; Logging
                 [org.clojure/tools.logging "1.3.0"]
                 [org.slf4j/slf4j-simple "2.0.13"]
                 ;; JSON
                 [cheshire "5.13.0"]]
  :main ^:skip-aot pearslcore-test.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[integrant/repl "0.3.3"]
                                 [ring/ring-mock "0.4.0"]]
                   :source-paths ["dev"]}}
  :plugins [[migratus-lein "0.7.3"]
            [lein-cljfmt "0.9.2"]]
  :migratus {:store :database
             :migration-dir "migrations"
             :db {:classname "org.sqlite.JDBC"
                  :subprotocol "sqlite"
                  :subname ".data/dev.db"}})
