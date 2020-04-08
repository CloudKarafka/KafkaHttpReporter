(ns cloudkarafka.kafka-http-reporter
  (:require [cloudkarafka.jmx :as jmx]
            [cloudkarafka.kafkaadmin :as ka]
            [clojure.string :as str]
            [aleph.http :as http]
            [jsonista.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :as params]
            [cloudkarafka.tcp :as tcp]
            [cloudkarafka.fast-jmx :as fjmx])
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
                       (let [plaintext-url (-> (:kafka-config s)
                                               :listeners 
                                               (listener-uri  "PLAINTEXT")
                                               first)]
                         (ka/consumer-groups-old plaintext-url (:consumer s))))]
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
    (reset! state {:kafka-version kafka-version
                   :kafka-config parsed-config
                   :admin-client (when (modern-kafka? kafka-version)
                                   (ka/admin-client props))
                   :consumer (ka/kafka-consumer props)})))

(defn -init [this metrics]
  (let [config (:kafka-config @state)
        http-port (Integer/parseInt (or (:kafka_http_reporter.port config) "19092"))
        tcp-port (Integer/parseInt (or (:kafka_http_reporter.tcp_port config) "19500"))
        tcp-server (tcp/tcp-server :port tcp-port :handler (tcp/wrap-io fjmx/handler))]
    (println "[INFO] KafkaHttpReporter: Starting HTTP server on port " http-port )
    (swap! state assoc :http-server (http/start-server handler {:port http-port}))
    (println "[INFO] KafkaHttpReporter: Starting TCP server on port " tcp-port)
    (swap! state assoc :tcp-server (tcp/start tcp-server))))

(defn -metricChange [this metric])
(defn -metricRemoval [this metric])

(defn -close [this]
  ;; No need to close this
  #_(let [s @state]
    (when-let [^java.io.Closeable server (:http-server s)]
      (println "[INFO] KafkaHttpReporter: Closing HTTP server")
      (.close server))
    (when-let [server (:tcp-server s)]
      (println "[INFO] KafkaHttpReporter: Closing TCP server")
      (tcp/stop server))))
