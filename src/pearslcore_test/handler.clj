(ns pearslcore-test.handler
  "HTTP handlers and routes"
  (:require [ring.util.response :as response]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.coercion.malli :as rcm]
            [reitit.ring.middleware.muuntaja :as muuntaja-middleware]
            [reitit.ring.middleware.parameters :as parameters-middleware]
            [pearslcore-test.handler.projects :as projects-handler]
            [pearslcore-test.middleware :as middleware]
            [pearslcore-test.schema :as schema]
            [muuntaja.core :as m]))

;; Muuntaja configuration
(def muuntaja
  "Muuntaja instance for JSON serialization"
  (m/create
   (-> m/default-options
       (assoc-in [:formats "application/json" :decoder-opts] {:keywords? true})
       (assoc-in [:formats "application/json" :encoder-opts] {:escape-non-ascii false}))))

;; Routes
(defn routes
  "Create routes with injected dependencies"
  [datasource config]
  (ring/router
   [["/openapi.yaml"
     {:get {:handler (fn [_]
                       (-> (response/resource-response "openapi.yaml" {:root "resources"})
                           (response/content-type "application/x-yaml")))}}]

    ["/projects"
     {:middleware [[middleware/wrap-version config]
                   [middleware/wrap-error]]}

     ["" {:get {:summary "List all projects"
                :description "Returns a paginated list of projects with optional filtering by status"
                :parameters {:query [:map {:closed false}
                                     [:limit {:optional true} [:int {:min 1 :max 100}]]
                                     [:offset {:optional true} [:int {:min 0}]]
                                     [:sort {:optional true} [:enum "created_at" "name"]]
                                     [:order {:optional true} [:enum "asc" "desc"]]
                                     [:status {:optional true} [:enum "planned" "active" "completed" "archived"]]]}
                :responses {200 {:description "Successful response"
                                 :body schema/project-list-response-schema}
                            400 {:description "Invalid query parameters"}}
                :handler (partial projects-handler/list-projects datasource)}

          :post {:summary "Create a new project"
                 :description "Creates a new project with the given name and optional status"
                 :responses {201 {:description "Project created"
                                  :body schema/project-response-schema}
                             400 {:description "Invalid request body"}
                             409 {:description "Duplicate project name"}
                             422 {:description "Validation error"}}
                 :handler (partial projects-handler/create-project datasource)}}]

     ["/:id" {:get {:summary "Get a project by ID"
                    :description "Returns a single project by its ID"
                    :parameters {:path [:map [:id string?]]}
                    :responses {200 {:description "Successful response"
                                     :body schema/project-response-schema}
                                400 {:description "Invalid UUID"}
                                404 {:description "Project not found"}}
                    :handler (partial projects-handler/get-project datasource)}}]]]

   {:data {:coercion rcm/coercion
           :muuntaja muuntaja
           :middleware [middleware/wrap-json-parse-error
                        parameters-middleware/parameters-middleware
                        muuntaja-middleware/format-negotiate-middleware
                        muuntaja-middleware/format-request-middleware
                        muuntaja-middleware/format-response-middleware
                        coercion/coerce-exceptions-middleware
                        coercion/coerce-request-middleware
                        coercion/coerce-response-middleware]}}))

(defn create-handler
  "Create the main handler with middleware"
  [datasource config]
  (ring/ring-handler
   (routes datasource config)
   (ring/create-default-handler)
   {:middleware [[middleware/wrap-logging]]}))
