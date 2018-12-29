(ns cloudkarafka.kafka-http-reporter
  (:require [cloudkarafka.jmx :as jmx]
            [cloudkarafka.kafkaadmin :as ka]
            [clojure.string :as str]
            [aleph.http :as http]
            [jsonista.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :as params])
  (:gen-class
   :implements [org.apache.kafka.common.metrics.MetricsReporter]
   :constructors {[] []}))

(set! *warn-on-reflection* true)

(def mapper (json/object-mapper {:decode-key-fn true, :encode-key-fn true}))

(def state (atom nil))

(defn listener-uri [listeners type]
  (->> (str/split listeners #",")
       (map #(str/split % #"://"))
       (filter #(= (first %) type))
       (map second)))

(defn modern-kafka? [version]
  (let [major (-> version
                  (str/split #"\.")
                  first
                  Integer/parseInt)]
    (>= major 2)))

(defn consumers
  ([] (consumers :group))
  ([group-by-fn]
   (let [s @state
         member-list (if (modern-kafka? (:kafka-version s))
                       (ka/consumer-groups (:admin-client s) (:consumer s))
                       (ka/consumer-groups-old (:admin-client s) (:consumer s)))]
     (group-by group-by-fn member-list))))

(def handler
  (params/wrap-params
   (routes
    (GET "/plugin-version" []
         {:status 200 :headers {"content-type" "text/plain"} :body "0.1.0"})

    (GET "/kafka-version" []
         {:status 200 :headers {"content-type" "text/plain"} :body (:kafka-version @state)})

    (GET "/jmx" [bean group attrs]
         (if-let [values (jmx/query bean group (str/split attrs #","))]
           {:status 200
            :headers {"content-type" "application/json"}
            :body (json/write-value-as-string values mapper)}
           {:status 404
            :body (str "Bean " bean " not found")}))

    (GET "/config" []
         (if-let [c (:kafka-config @state)]
           {:status 200
            :headers {"content-type" "application/json"}
            :body (json/write-value-as-string c mapper)}
           {:status 404
            :body "No config"}))
    (GET "/consumer-groups" []
         {:status 200
          :headers {"content-type" "application/json"}
          :body (json/write-value-as-string (consumers) mapper)})
    (route/not-found "Not found"))))

(defn -configure [this config]
  (let [parsed-config (into {} (map (fn [[k v]] [(keyword k) v]) config))
        uris (listener-uri (:listeners parsed-config) "PLAINTEXT")
        props {:bootstrap.servers (first uris)}
        kafka-version (org.apache.kafka.common.utils.AppInfoParser/getVersion)]
    (println "-------------- KAFKA-VERSION" kafka-version)
    (reset! state {:kafka-version kafka-version
                   :kafka-config parsed-config
                   :admin-client (if (modern-kafka? kafka-version)
                                   (ka/admin-client props)
                                   (ka/admin-client-old props))
                   :consumer (ka/kafka-consumer props)})))

(defn -init [this metrics]
  (let [config (:kafka-config @state)
        port (Integer/parseInt (or (:kafka_http_reporter.port config) "19092"))]
    (println "[INFO] KafkaHttpReporter: Starting HTTP server on port " port )
    (swap! state assoc :http-server (http/start-server handler {:port port}))))

(defn -metricChange [this metric])
(defn -metricRemoval [this metric])

(defn -close [this]
  (when-let [^java.io.Closeable s (:http-server @state)]
    (println "[INFO] KafkaHttpReporter: Closing HTTP server")
    (.close s)))
