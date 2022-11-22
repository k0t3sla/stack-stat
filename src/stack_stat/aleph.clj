(ns stack-stat.aleph
   (:require [aleph.http :as http]
             [stack-stat.common :refer [parse-tags get-tag-stat]]
             [manifold.deferred :as d]
             [manifold.executor :as e]
             [compojure.core :refer [defroutes GET]]
             [compojure.route :refer [not-found]]
             [hiccup.page :as hiccup]
             [cheshire.core :as cheshire]
             [clj-commons.byte-streams :as bs])
  (:import java.io.BufferedReader java.io.InputStreamReader java.util.zip.GZIPInputStream)
  (:gen-class))

(defn parse-json-input-stream  
  "преобразования входящего потока, aleph не умеет в gzip"
  [stream] 
  (let [parsed-data (-> stream
                        GZIPInputStream.
                        InputStreamReader.
                        BufferedReader.
                        cheshire/parse-stream)]
    parsed-data))

(defn api-call!
  [tag]
  (let [input-stream (-> @(http/get "https://api.stackexchange.com/2.2/search"
                                    {:query-params {:pagesize 100
                                                    :order "desc"
                                                    :sort "creation"
                                                    :tagged tag
                                                    :site "stackoverflow"}})
                         :body
                         bs/to-input-stream)]
    {tag (get-tag-stat (parse-json-input-stream input-stream))}))

(defn manifold-api-call
  [ex x]
  (let [d (d/deferred ex)
        c (d/chain d #(future (api-call! %)))]
    (d/success! d x)
    c))

(defn request-stackoverflow [tags treads]
  (apply d/zip (mapv (partial manifold-api-call treads) 
                     tags)))

(defonce server (atom nil))

(defn app [req]
  (let [qs (:query-string req)
        tags (if (not-empty qs)
                (parse-tags qs)
                nil)
        treads (e/fixed-thread-executor 4)
        reqest (when-not (nil? tags) (request-stackoverflow tags treads))] 
    (if (nil? tags)
      {:status 400
       :body "Не верный запрос"
       :headers {:content-type "json"}}
      {:status 200
       :body (cheshire/generate-string
              @reqest
              {:pretty true})
       :headers {:content-type "json"}})))

(defroutes routes
  (GET "/search" [] app)
  (not-found (hiccup/html5 [:h2 "page not found"])))

(defn start-server []
  (println "Starting aleph server at http://localhost:8081/")
  (reset! server
          (http/start-server routes 
                             {:port 8081})))

(defn stop-server []
  (when-some [s @server]
    (.close s)
    (reset! server nil)))

(comment 
  (start-server) 
  (stop-server)
  )