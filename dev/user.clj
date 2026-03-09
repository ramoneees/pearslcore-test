(ns user
  "REPL development helpers"
  (:require [integrant.repl :refer [go halt reset]]
            [integrant.repl.state]
            [pearslcore-test.system :as system]))

(integrant.repl/set-prep! (constantly system/system-config))

(defn start []
  (go))

(defn stop []
  (halt))

(defn restart []
  (reset))

(comment
  ;; Start the system
  (start)

  ;; Stop the system
  (stop)

  ;; Restart the system
  (restart)

  ;; Access the running system
  integrant.repl.state/system

  ;; Access the datasource
  (:pearslcore/datasource integrant.repl.state/system)

  ;; Test a query
  (require '[next.jdbc.sql :as sql])
  (sql/query (:pearslcore/datasource integrant.repl.state/system)
             ["SELECT * FROM projects LIMIT 5"]))
