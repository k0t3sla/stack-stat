(ns stack-stat.common
  (:require [ring.util.codec :refer [form-decode]]
            [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.walk :as walk]))

(defn parse-tags 
  "парсим параметры запроса
   и фильтруем чтобы не было пустых строк"
  [params]
  (let [decoded (->> (form-decode params)
                    walk/keywordize-keys)
        tags (if (string? (:tag decoded))
               [(:tag decoded)]
               (:tag decoded)) 
        not-blank (filterv (complement string/blank?) tags)]
    not-blank))

(defn check-answerd [tag]
  (if
   (true? ^boolean (get tag "is_answered"))
    1
    0))

(defn get-tag-stat [input]
  (loop [xs (get input "items")
         answerd 0
         total 0]
    (if xs
      (let [x (first xs)]
        (recur (next xs)
               (+ answerd (check-answerd x))
               (+ total (count (get x "tags")))))
      {:total total
       :answerd answerd})))