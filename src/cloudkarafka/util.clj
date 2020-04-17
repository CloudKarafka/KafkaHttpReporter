(ns cloudkarafka.util
  (:require [jsonista.core :as json]
            [clojure.string :as str]))

(def mapper (json/object-mapper {:decode-key-fn true, :encode-key-fn true}))

(def state (atom nil))

(def kafka-version ( org.apache.kafka.common.utils.AppInfoParser/getVersion))

(defn listener-uri [listeners type]
  (->> (str/split listeners #",")
       (map #(str/split % #"://"))
       (filter #(= (first %) type))
       (map second)))

(defn modern-kafka?
  ([] (modern-kafka? kafka-version))
  ([version]
   (let [major (-> version
                   (str/split #"\.")
                   first
                   Integer/parseInt)]
     (>= major 2))))
