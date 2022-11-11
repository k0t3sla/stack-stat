(ns stack-stat.core
  (:require [clojure.core.async :as a]
            [org.httpkit.client :as client]
            [clojure.pprint :as pprint]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [ring.util.codec :refer [form-decode]]
            [org.httpkit.server :as http])
  (:gen-class))

(defn is-answered [tag]
  (->> tag
       :items
       (map :is_answered)
       (filter true?)
       count))

(defn total [tag]
  (->> tag
       :items
       (map :tags)
       (map count)
       (reduce +)))

(defn- seq-of-chanels [c]
  (lazy-seq
   (let [fst (a/<!! c)]
     (if
      (nil? fst) nil
      (cons fst (seq-of-chanels c))))))

(defn map-pipeline-async
  [async-function p coll]
   (let [in-c (a/chan p)
         out-c (a/chan p)]
     (a/onto-chan! in-c coll)
     (a/pipeline-async p out-c async-function in-c)
     (seq-of-chanels out-c)))

(defn parse-tags [params]
  (let [tags (->> (form-decode params)
                  walk/keywordize-keys)]
    (cond
      (string? (:tag tags)) {:tag (:tag tags)}
      (vector? (:tag tags)) (vec (for [tag (:tag tags)]
                                   {:tag tag}))
      :else nil)))

(defn format-tag-data [page body]
  (let [parsed-data (walk/keywordize-keys (cheshire/parse-string body))]
    {(:tag page) {:total (total (walk/keywordize-keys parsed-data))
                  :answered (is-answered (walk/keywordize-keys parsed-data))}}))


(defn request-stackoverflow [tags treads]
  (map-pipeline-async
   (fn [{:keys [tag] :as page} c]
     (client/get "https://api.stackexchange.com/2.2/search"
                 {:query-params {:pagesize 100
                                 :order "desc"
                                 :sort "creation"
                                 :tagged tag
                                 :site "stackoverflow"}}
                 (fn [{:keys [body]}]
                   (when (string? body)
                     (a/>!! c (format-tag-data page body)))
                   (a/close! c))))
   treads tags))
  
(defn app [req]
  (let [qs (:query-string req)
        tags (parse-tags qs)
        treads 2
        request-stackoverflow (request-stackoverflow tags treads)
        formated-output (cheshire/generate-string
                         (reduce into {} request-stackoverflow)
                         {:pretty true})]
    {:status 200
     :body (if (nil? tags)
             "Не верный запрос"
             formated-output)
     :headers {}}))


(defn start-server []
  (println "starting httpkit server http://localhost:8080/")
  (http/run-server
   (fn [req] (app req))
   {:port 8080}))

(defn -main
  []
  (start-server))


(comment 
  (start-server)
  )
  