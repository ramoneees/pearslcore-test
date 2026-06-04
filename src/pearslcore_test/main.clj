(ns pearslcore-test.main
  "Main entry point for the Projects API"
  (:require [pearslcore-test.system :as system])
  (:gen-class))

(defn -main
  "Start the Projects API server"
  [& _]
  (println "Starting Projects API...")
  (system/start)
  (deref (promise)))
