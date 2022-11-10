(ns stack-stat.core
  (:require [clojure.core.async :as a]
            [org.httpkit.client :as client]
            [clojure.pprint :as pprint]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
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
  ([async-function p coll]
   (let [in-c (a/chan p)
         out-c (a/chan p)]
     (a/onto-chan! in-c coll)
     (a/pipeline-async p out-c async-function in-c)
     (seq-of-chanels out-c))))

(defonce server (atom nil))

(defn app [req]
  (let [qs (:query-string req)]
    {:status 200
     :body (with-out-str (pprint/pprint qs))
     :headers {}}))

(defn start-server []
  (reset! server
          (http/run-server (fn [req] (app req))
                           {:port 8080})))

(defn stop-server []
  (when-some [s @server]
    (http/server-status s)
    (reset! server nil)))


(def tags 
  [{:tag "clojure"}
   {:tag "python"}])

(defn format-data [page body]
  (let [parsed-data (walk/keywordize-keys (cheshire/parse-string body))]
    {(:tag page) {:total (total (walk/keywordize-keys parsed-data))
                  :answered (is-answered (walk/keywordize-keys parsed-data))}}))


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
                 (a/>!! c (format-data page body)))
               (a/close! c))))
 2 tags)


(comment

  (start-server)

  (stop-server))


(defn -main
  []
  #_(start-server))