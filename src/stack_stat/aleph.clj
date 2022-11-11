(ns stack-stat.aleph
   (:require [aleph.http :as http]
             [aleph.http.params :refer [parse-params]]
             [manifold.deferred :as d]
             [clojure.walk :as walk]
             [clojure.pprint :as pprint]
             [cheshire.core :as cheshire]
             [clj-commons.byte-streams :as bs])
  (:import java.io.BufferedReader java.io.InputStreamReader java.util.zip.GZIPInputStream)
  (:gen-class))

(def raw-stream-connection-pool (http/connection-pool {:connection-options {:connections-per-host 4 :raw-stream? true}}))

(defn parse-json-input-stream [stream]
  (-> stream
      GZIPInputStream.
      InputStreamReader.
      BufferedReader.
      cheshire/parse-stream
      walk/keywordize-keys))

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

(def data (-> @(http/get "https://api.stackexchange.com/2.2/search"
                         {:query-params {:pagesize 100
                                         :order "desc"
                                         :sort "creation"
                                         :tagged "clojure"
                                         :site "stackoverflow"}
                          :pool raw-stream-connection-pool})
              :body
              bs/to-input-stream
              parse-json-input-stream))


(defonce server (atom nil))

(defn app [req]
  (let [qs (parse-params (:query-string req))]
    {:status 200
     :body (with-out-str (pprint/pprint qs))
     :headers {}}))

(defn start-server []
  (reset! server
          (http/start-server (fn [req] (app req))
                              {:port 8080})))

(defn stop-server []
  (when-some [s @server]
    (.close s)
    (reset! server nil)))

(comment

  (start-server)
  (stop-server)

  (client/get "https://api.stackexchange.com/2.2/search?pagesize=100&order=desc&sort=creation&tagged=clojure&site=stackoverflow" {:accept :json})

  ;;https://aleph.io/examples/literate.html#aleph.examples.http

  (-> @(http/get "https://jsonplaceholder.typicode.com/todos/1")
      :body
      parse-json-input-stream)


  @(http/get "https://jsonplaceholder.typicode.com/todos/1")
  )


(defn start-aleph
  []
  #_(start-server))