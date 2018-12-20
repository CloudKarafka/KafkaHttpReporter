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

(def http-server (atom nil))
(def admin-client (atom nil))
(def consumer (atom nil))
(def kafka-config (atom nil))

(defn listener-uri [listeners type]
  (->> (str/split listeners #",")
       (map #(str/split % #"://"))
       (filter #(= (first %) type))
       (map second)))

(def handler
  (params/wrap-params
   (routes
    (GET "/plugin-version" []
         {:status 200 :headers {"content-type" "text/plain"} :body "0.1.0"})
    (GET "/kafka-version" []
         (let [values (jmx/query "kafka.server:type=app-info" nil ["version"])]
           {:status 200 :headers {"content-type" "text/plain"} :body (:value (first values))}))
    (GET "/jmx" [bean group attrs]
         (if-let [values (jmx/query bean group (str/split attrs #","))]
           {:status 200
            :headers {"content-type" "application/json"}
            :body (json/write-value-as-string values mapper)}
           {:status 404
            :body (str "Bean " bean " not found")}))
    (GET "/config" []
         (if-let [c @kafka-config]
           {:status 200
            :headers {"content-type" "application/json"}
            :body (json/write-value-as-string c mapper)}
           {:status 404
            :body "No config"}))
    (GET "/consumer-groups" []
         (let [v (ka/consumer-groups @admin-client @consumer)]
           {:status 200
            :headers {"content-type" "application/json"}
            :body (json/write-value-as-string v mapper)}))
    (route/not-found "Not found"))))

(defn -configure [this config]
  (let [parsed-config (into {} (map (fn [[k v]] [(keyword k) v]) config))
        uris (listener-uri (:listeners parsed-config) "PLAINTEXT")
        props {:bootstrap.servers (first uris)}]
    (reset! kafka-config parsed-config)
    (reset! admin-client (ka/admin-client props))
    (reset! consumer (ka/kafka-consumer props))))

(defn -init [this metrics]
  (let [config @kafka-config
        port (Integer/parseInt (or (:kafka_http_reporter.port config) "19092"))]
    (println "[INFO] KafkaHttpReporter: Starting HTTP server on port " port )
    (reset! http-server (http/start-server handler {:port port}))))

(defn -metricChange [this metric])
(defn -metricRemoval [this metric])

(defn -close [this]
  (when-let [^java.io.Closeable s @http-server]
    (println "[INFO] KafkaHttpReporter: Closing HTTP server")
    (.close s)))
