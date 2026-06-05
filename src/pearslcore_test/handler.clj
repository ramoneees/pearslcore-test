(ns pearslcore-test.handler
  "HTTP handlers and routes"
  (:require [ring.util.response :as response]
            [clojure.java.io :as io]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.coercion.malli :as rcm]
            [reitit.ring.middleware.muuntaja :as muuntaja-middleware]
            [reitit.ring.middleware.parameters :as parameters-middleware]
            [reitit.swagger-ui :as swagger-ui]
            [pearslcore-test.handler.projects :as projects-handler]
            [pearslcore-test.middleware :as middleware]
            [pearslcore-test.schema :as schema]
            [malli.transform :as mt]
            [muuntaja.core :as m]))

(def ^:private malli-coercion
  "Reitit malli coercion that preserves each schema's own :closed setting
   (default reitit recompiles schemas, which can mask our closed body schema)
   and uses a no-op body transformer so extra keys survive to the validation
   step and are rejected by the closed-map check."
  (rcm/create
   (-> rcm/default-options
       (assoc :compile (fn [schema _opts] schema))
       (assoc-in [:transformers :body :default] (mt/transformer))
       (assoc-in [:transformers :body :formats "application/json"] (mt/transformer)))))

(def muuntaja
  "Muuntaja instance for JSON serialization"
  (m/create
   (-> m/default-options
       (assoc-in [:formats "application/json" :decoder-opts] {:keywords? true})
       (assoc-in [:formats "application/json" :encoder-opts] {:escape-non-ascii false}))))

(defn- humanized->details
  "Walk a Malli humanized error map into a [{:field :message}] vector.
   Reports every failing field, not just the first."
  [humanized]
  (letfn [(walk [parent x]
            (cond
              (map? x)
              (vec (mapcat (fn [[k v]] (walk k v)) x))

              (sequential? x)
              (mapv (fn [msg]
                      {:field (cond
                                (keyword? parent) (name parent)
                                (some? parent) (str parent)
                                :else "body")
                       :message (str msg)})
                    x)

              :else
              [{:field (cond
                         (keyword? parent) (name parent)
                         (some? parent) (str parent)
                         :else "body")
                :message (str x)}]))]
    (vec (walk nil humanized))))

(defn- coercion-error-response
  "Map a Reitit coercion failure to our standardized error envelope.
   Body failures → 422 validation_error; query/path failures → 400 bad_request."
  [{:keys [in humanized]}]
  (let [body-failure? (some #{:body-params} in)
        details (humanized->details humanized)]
    (if body-failure?
      {:status 422
       :body {:error {:code "validation_error"
                      :message "Request validation failed"
                      :details details}}
       :headers {"Content-Type" "application/json"}}
      {:status 400
       :body {:error {:code "bad_request"
                      :message "Invalid request parameters"
                      :details details}}
       :headers {"Content-Type" "application/json"}})))

(def ^:private custom-coerce-exceptions-middleware
  "Replaces reitit's default coerce-exceptions middleware so body coercion
   failures return 422 (semantic) and query/path failures return 400 (syntactic),
   both shaped to our error contract."
  {:name ::coerce-exceptions
   :wrap (fn [handler]
           (fn coerce-exceptions
             ([request]
              (try
                (handler request)
                (catch clojure.lang.ExceptionInfo e
                  (let [data (ex-data e)]
                    (if (= :reitit.coercion/request-coercion (:type data))
                      (coercion-error-response data)
                      (throw e))))))
             ([request respond raise]
              (try
                (handler request respond
                         (fn [e]
                           (let [data (ex-data e)]
                             (if (= :reitit.coercion/request-coercion (:type data))
                               (respond (coercion-error-response data))
                               (raise e)))))
                (catch clojure.lang.ExceptionInfo e
                  (let [data (ex-data e)]
                    (if (= :reitit.coercion/request-coercion (:type data))
                      (respond (coercion-error-response data))
                      (raise e))))))))})

(defn routes
  "Create routes with injected dependencies"
  [datasource config]
  (ring/router
   [["/openapi.yaml"
     {:get {:handler (fn [_]
                       (-> (response/response (-> "openapi.yaml" io/resource io/input-stream))
                           (response/content-type "application/x-yaml")))}}]

    ["/swagger*"
     {:no-doc true
      :get (swagger-ui/create-swagger-ui-handler
            {:path "/swagger"
             :url "/openapi.yaml"
             :config {:validatorUrl nil}})}]

    ["/projects"
     {:middleware [[middleware/wrap-version config]
                   [middleware/wrap-error]]}

     ["" {:get {:summary "List all projects"
                :description "Returns a paginated list of projects with optional filtering by status"
                :parameters {:query schema/list-projects-params-schema}
                :responses {200 {:description "Successful response"
                                 :body schema/project-list-response-schema}
                            400 {:description "Invalid query parameters"}}
                :handler (partial projects-handler/list-projects datasource)}

          :post {:summary "Create a new project"
                 :description "Creates a new project with the given name and optional status"
                 :parameters {:body schema/create-project-request-schema}
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

   {:data {:coercion malli-coercion
           :muuntaja muuntaja
           :middleware [middleware/wrap-json-parse-error
                        parameters-middleware/parameters-middleware
                        muuntaja-middleware/format-negotiate-middleware
                        muuntaja-middleware/format-request-middleware
                        muuntaja-middleware/format-response-middleware
                        custom-coerce-exceptions-middleware
                        coercion/coerce-request-middleware
                        coercion/coerce-response-middleware]}}))

(defn create-handler
  "Create the main handler with middleware"
  [datasource config]
  (ring/ring-handler
   (routes datasource config)
   (ring/create-default-handler)
   {:middleware [[middleware/wrap-logging]]}))
