(ns pearslcore-test.handler.projects-test
  "Integration tests for the Projects API"
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [ring.mock.request :as mock]
            [cheshire.core :as json]
            [pearslcore-test.handler :as handler]
            [pearslcore-test.db.datasource :as datasource]
            [pearslcore-test.db.migrations :as migrations])
  (:import [java.util UUID]))

;; Test database setup
(def ^:dynamic *test-datasource* nil)

(defn with-test-database [f]
  (let [db-file (str ".data/test-" (UUID/randomUUID) ".db")
        config {:db {:classname "org.sqlite.JDBC"
                     :subprotocol "sqlite"
                     :subname db-file
                     :pool {:maximum-pool-size 2
                            :minimum-idle 1}}}
        ds (datasource/create-datasource config)]
    (try
      ;; Run migrations
      (migrations/migrate! ds)
      ;; Bind the datasource
      (binding [*test-datasource* ds]
        (f))
      (finally
        ;; Cleanup
        (datasource/close-datasource ds)
        (let [file (java.io.File. db-file)]
          (when (.exists file)
            (.delete file)))))))

(use-fixtures :each with-test-database)

;; Helper functions
(defn parse-json [body]
  (if (string? body)
    (json/parse-string body true)
    (if (instance? java.io.InputStream body)
      (json/parse-string (slurp body) true)
      body)))

(defn make-request
  "Make a mock request to the handler"
  [ds method uri & {:keys [body params headers]}]
  (let [handler (handler/create-handler ds {:http {:port 3000}
                                            :api {:default-version 1
                                                  :supported-versions #{1}}})
        request (cond-> (mock/request method uri)
                  body (mock/json-body body)
                  params (mock/query-string params)
                  headers (update :headers merge headers))
        response (handler request)]
    response))

;; Tests

(deftest list-projects-happy-path
  (testing "List projects returns paginated results"
    (let [response (make-request *test-datasource* :get "/projects")
          body (parse-json (:body response))]
      (is (= 200 (:status response)))
      (is (contains? body :items))
      (is (contains? body :limit))
      (is (contains? body :offset))
      (is (contains? body :total)))))

(deftest create-project-happy-path
  (testing "Create a project with minimal data"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "Test Project"})
          body (parse-json (:body response))]
      (is (= 201 (:status response)))
      (is (= "Test Project" (:name body)))
      (is (= "planned" (:status body)))
      (is (some? (:id body)))
      (is (some? (:created_at body)))
      (is (some? (get-in response [:headers "Location"])))))

  (testing "Create a project with status"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "Active Project"
                                        :status "active"})
          body (parse-json (:body response))]
      (is (= 201 (:status response)))
      (is (= "Active Project" (:name body)))
      (is (= "active" (:status body))))))

(deftest get-project-by-id-happy-path
  (testing "Get a project by ID"
    (let [create-response (make-request *test-datasource* :post "/projects"
                                        :body {:name "Get Test Project"})
          create-body (parse-json (:body create-response))
          project-id (:id create-body)
          response (make-request *test-datasource* :get (str "/projects/" project-id))
          body (parse-json (:body response))]
      (is (= 200 (:status response)))
      (is (= project-id (:id body)))
      (is (= "Get Test Project" (:name body))))))

(deftest invalid-name-validation
  (testing "Name too short"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "ab"})
          body (parse-json (:body response))]
      (is (= 422 (:status response)))
      (is (= "validation_error" (get-in body [:error :code])))))

  (testing "Name too long"
    (let [long-name (apply str (repeat 121 "a"))
          response (make-request *test-datasource* :post "/projects"
                                 :body {:name long-name})
          body (parse-json (:body response))]
      (is (= 422 (:status response)))
      (is (= "validation_error" (get-in body [:error :code])))))

  (testing "Name missing"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {})
          body (parse-json (:body response))]
      (is (= 422 (:status response)))
      (is (= "validation_error" (get-in body [:error :code]))))))

(deftest duplicate-name-conflict
  (testing "Create project with duplicate name"
    ;; Create first project
    (make-request *test-datasource* :post "/projects"
                  :body {:name "Unique Project"})
    ;; Try to create with same name
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "Unique Project"})
          body (parse-json (:body response))]
      (is (= 409 (:status response)))
      (is (= "conflict" (get-in body [:error :code])))))

  (testing "Create project with case-insensitive duplicate name"
    (make-request *test-datasource* :post "/projects"
                  :body {:name "Case Project"})
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "case project"})]
      (is (= 409 (:status response))))))

(deftest missing-project-not-found
  (testing "Get non-existent project"
    (let [response (make-request *test-datasource* :get
                                 "/projects/00000000-0000-0000-0000-000000000000")
          body (parse-json (:body response))]
      (is (= 404 (:status response)))
      (is (= "not_found" (get-in body [:error :code]))))))

(deftest invalid-uuid-path
  (testing "Get project with invalid UUID"
    (let [response (make-request *test-datasource* :get "/projects/not-a-uuid")
          body (parse-json (:body response))]
      (is (= 400 (:status response)))
      (is (= "bad_request" (get-in body [:error :code]))))))

(deftest bad-pagination-params
  (testing "Invalid limit value"
    (let [response (make-request *test-datasource* :get "/projects"
                                 :params {:limit "invalid"})]
      (is (= 400 (:status response)))))

  (testing "Limit out of range"
    (let [response (make-request *test-datasource* :get "/projects"
                                 :params {:limit "0"})]
      (is (<= 400 (:status response)))))

  (testing "Invalid offset value"
    (let [response (make-request *test-datasource* :get "/projects"
                                 :params {:offset "invalid"})]
      (is (= 400 (:status response))))))

(deftest status-filter-works
  (testing "Filter projects by status"
    ;; Create projects with different statuses
    (make-request *test-datasource* :post "/projects"
                  :body {:name "Planned Project" :status "planned"})
    (make-request *test-datasource* :post "/projects"
                  :body {:name "Active Project" :status "active"})
    (make-request *test-datasource* :post "/projects"
                  :body {:name "Completed Project" :status "completed"})

    ;; Filter by active
    (let [response (make-request *test-datasource* :get "/projects"
                                 :params {:status "active"})
          body (parse-json (:body response))]
      (is (= 200 (:status response)))
      (is (every? #(= "active" (:status %)) (:items body))))))

(deftest api-version-header
  (testing "Supported version works"
    (let [response (make-request *test-datasource* :get "/projects"
                                 :headers {"x-api-version" "1"})]
      (is (= 200 (:status response)))))

  (testing "Unsupported version returns error"
    (let [response (make-request *test-datasource* :get "/projects"
                                 :headers {"x-api-version" "2"})
          body (parse-json (:body response))]
      (is (= 400 (:status response)))
      (is (= "bad_request" (get-in body [:error :code]))))))

(deftest forbidden-fields-rejected
  (testing "Cannot specify id on create"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "Test"
                                        :id "550e8400-e29b-41d4-a716-446655440099"})]
      (is (= 422 (:status response)))))

  (testing "Cannot specify created_at on create"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "Test"
                                        :created_at "2026-01-01T00:00:00Z"})]
      (is (= 422 (:status response))))))

(deftest unknown-fields-rejected
  (testing "Unknown fields are rejected"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "Test"
                                        :unknown_field "value"})]
      (is (= 422 (:status response))))))

(deftest name-trimming
  (testing "Name with leading/trailing whitespace is trimmed"
    (let [response (make-request *test-datasource* :post "/projects"
                                 :body {:name "  Trimmed Name  "})
          body (parse-json (:body response))]
      (is (= 201 (:status response)))
      (is (= "Trimmed Name" (:name body))))))
