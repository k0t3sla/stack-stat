(ns stack-stat.common
  (:require [ring.util.codec :refer [form-decode]]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]))

(defn is-answered 
  "получаем количество отвеченных вопросов"
  [tag]
  (->> tag
       :items
       (map :is_answered)
       (filter true?)
       count))

(defn total 
  "получаем суммарное колличество тегов"
  [tag]
  (->> tag
       :items
       (map :tags)
       (map count)
       (reduce +)))

(defn parse-tags 
  "парсим параметры запроса"
  [params]
  (let [tags (->> (form-decode params)
                  walk/keywordize-keys)]
    (if (string? (:tag tags))
      [(:tag tags)]
      (:tag tags))))

(defn format-tag-data 
  "форматируем для корректного json на выходе"
  [tag body] 
  (let [parsed-data (walk/keywordize-keys (cheshire/parse-string body))]
    {(keyword tag) {:total (total (walk/keywordize-keys parsed-data))
                    :answered (is-answered (walk/keywordize-keys parsed-data))}}))