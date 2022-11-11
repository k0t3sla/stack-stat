(ns stack-stat.core
  (:require [clojure.core.async :as a]
            [org.httpkit.client :as client]
            [clojure.pprint :as pprint]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [ring.util.codec :refer [form-decode]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [files not-found]]
            [hiccup.page :as hiccup]
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
    (if (string? (:tag tags))
      [(:tag tags)]
      (:tag tags))))

(defn format-tag-data [tag body]
  (let [parsed-data (walk/keywordize-keys (cheshire/parse-string body))]
    {(keyword tag) {:total (total (walk/keywordize-keys parsed-data))
                    :answered (is-answered (walk/keywordize-keys parsed-data))}}))

(defn make-async-reqest [tag c]
  (client/get
   "https://api.stackexchange.com/2.2/search"
   {:query-params {:pagesize 100
                   :order "desc"
                   :sort "creation"
                   :tagged tag
                   :site "stackoverflow"}}
   (fn [{:keys [body]}]
     (when (string? body)
       (a/>!! c (format-tag-data tag body)))
     (a/close! c))))

(defn request-stackoverflow [tags treads]
  (map-pipeline-async make-async-reqest treads tags))

(defn app [req]
  (let [qs (:query-string req)
        tags (parse-tags qs)
        treads 2
        reqest (when-not (nil? tags) (request-stackoverflow tags treads))
        formated-output (cheshire/generate-string
                         reqest
                         {:pretty true})]
    (if (nil? tags)
      {:status 400
       :body "Не верный запрос"
       :headers {}}
      {:status 200
       :body formated-output
       :headers {}})))

;; serv

(defn home [req]
  (hiccup/html5
   [:head (hiccup/include-css "styles.css")]
   [:div.content
    [:h2 "HOME"]]))

(defroutes routes
  (GET "/" [] home)
  (GET "/search" [] app)
  (files "/static/")
  (not-found (hiccup/html5 [:h2 :sty "page not found"])))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defn start-server []
  (reset! server (http/run-server #'routes {:port 8080})))

(defn -main
  []
  (println "starting http.kit server http://localhost:8080/")
  (start-server)) 

(comment
  (start-server) 
  (stop-server)
  )