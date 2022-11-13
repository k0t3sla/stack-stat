(ns stack-stat.core-test
  (:require [clojure.test :refer :all]
            [stack-stat.common :as common]
            [stack-stat.aleph :as al]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [stack-stat.core :as core]))

(deftest parse-params-test
  (testing "testing parsing query params"
    (is (= ["clojure" "python"]
           (common/parse-tags "tag=clojure&tag=python")))
    (is (= nil
           (common/parse-tags "")))
    (is (= nil
           (common/parse-tags "wrong_key=clojure")))))

(s/def ::tag (s/and string?
                    #(not (string/blank? %))))

(s/def ::stat (s/map-of string? number?
                        string? number?))
(s/def ::stack-stat 
  (s/map-of ::tag ::stat))

(deftest httpkit-test
  (testing "testing httpkit server"
    (core/start-server)
    (let [req (client/get
               "http://localhost:8080/search"
               {:query-params {:tag "clojure"}})
          resp (cheshire/decode (:body req))]
      (is (= 200 (:status req)))
      (is (= true (s/valid? ::stack-stat (first resp)))))
    (core/stop-server)))

(deftest aleph-test
  (testing "testing aleph server"
    (al/start-server)
    ()
    (let [req (client/get
               "http://localhost:8081/search"
               {:query-params {:tag "кложур"}})
          resp (cheshire/decode (:body req))]
      (is (= 200 (:status req)))
      (is (= true (s/valid? ::stack-stat (first resp)))))
    (al/stop-server)))