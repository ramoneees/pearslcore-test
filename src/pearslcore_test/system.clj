(ns pearslcore-test.system
  "Integrant system configuration and lifecycle management"
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [pearslcore-test.config :as config]
            [pearslcore-test.db.datasource :as datasource]
            [pearslcore-test.db.migrations :as migrations]
            [pearslcore-test.handler :as handler]
            [ring.adapter.jetty :as jetty]))

(def system-config
  "Integrant configuration map"
  {:pearslcore/config {}
   :pearslcore/datasource {:config (ig/ref :pearslcore/config)}
   :pearslcore/migrations {:datasource (ig/ref :pearslcore/datasource)}
   :pearslcore/handler {:datasource (ig/ref :pearslcore/datasource)
                        :config (ig/ref :pearslcore/config)}
   :pearslcore/server {:handler (ig/ref :pearslcore/handler)
                       :config (ig/ref :pearslcore/config)}})

(defmethod ig/init-key :pearslcore/config [_ _]
  (log/info "Initializing configuration")
  (config/get-config))

(defmethod ig/halt-key! :pearslcore/config [_ _]
  (log/info "Halting configuration"))

(defmethod ig/init-key :pearslcore/datasource [_ {:keys [config]}]
  (log/info "Initializing datasource")
  (datasource/create-datasource config))

(defmethod ig/halt-key! :pearslcore/datasource [_ ds]
  (log/info "Halting datasource")
  (datasource/close-datasource ds))

(defmethod ig/init-key :pearslcore/migrations [_ {:keys [datasource]}]
  (log/info "Running migrations")
  (migrations/migrate! datasource)
  {:datasource datasource})

(defmethod ig/halt-key! :pearslcore/migrations [_ _]
  (log/info "Halting migrations"))

(defmethod ig/init-key :pearslcore/handler [_ {:keys [datasource config]}]
  (log/info "Initializing HTTP handler")
  (handler/create-handler datasource config))

(defmethod ig/halt-key! :pearslcore/handler [_ _]
  (log/info "Halting HTTP handler"))

(defmethod ig/init-key :pearslcore/server [_ {:keys [handler config]}]
  (let [port (get-in config [:http :port] 3000)]
    (log/info "Starting HTTP server on port" port)
    (jetty/run-jetty handler {:port port :join? false})))

(defmethod ig/halt-key! :pearslcore/server [_ server]
  (log/info "Stopping HTTP server")
  (.stop server))

(defonce ^:dynamic *system* nil)

(defn start []
  (when *system*
    (ig/halt! *system*))
  (alter-var-root #'*system* (constantly (ig/init system-config)))
  *system*)

(defn stop []
  (when *system*
    (ig/halt! *system*)
    (alter-var-root #'*system* (constantly nil))))

(defn reset []
  (stop)
  (start))

(defn -main [& _]
  (start)
  (deref (promise)))
