(ns cloudkarafka.kafka-http-reporter
  (:require [cloudkarafka.jmx :as jmx]
            [cloudkarafka.kafkaadmin :as ka]
            [cloudkarafka.cmds :refer [exec format-result]]
            [cloudkarafka.util :as util]
            [clojure.string :as str]
            [aleph.http :as http]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [jsonista.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :as params])
  (:gen-class
   :implements [org.apache.kafka.common.metrics.MetricsReporter]
   :constructors {[] []}))

(set! *warn-on-reflection* true)

(def handler
  (params/wrap-params
   (routes
    (GET "/plugin-version" []
         {:status 200 :headers {"content-type" "text/plain"} :body "0.1.0"})

    (GET "/kafka-version" []
         {:status 200 :headers {"content-type" "text/plain"} :body (:kafka-version @util/state)})

    (GET "/jmx" [bean group attrs]
         (if-let [values (jmx/query bean group (str/split attrs #","))]
           {:status 200
            :headers {"content-type" "application/json"}
            :body (json/write-value-as-string values util/mapper)}
           {:status 404
            :body (str "Bean " bean " not found")}))

    (GET "/config" []
         (if-let [c (:kafka-config @util/state)]
           {:status 200
            :headers {"content-type" "application/json"}
            :body (json/write-value-as-string c util/mapper)}
           {:status 404
            :body "No config"}))

    (GET "/consumer-groups" []
         {:status 200
          :headers {"content-type" "application/json"}
          :body (json/write-value-as-string (group-by :group (ka/consumers)) util/mapper)})

    (route/not-found "Not found"))))

(defn wrapper [f]
  (fn [s _info]
    (s/connect (s/map f s) s)))

(defn tcp-handler [^bytes in]
  (let [s (str/trim (String. in))
        [cmd bean] (str/split s #" ")]
    (when-not (empty? cmd)
      (let [res (exec cmd bean)]
        (str (when res (format-result res)) "\n")))))

(defn -configure [_ config]
  (println "[INFO] KafkaHttpReporter: configure")
  (let [parsed-config (into {} (map (fn [[k v]] [(keyword k) v]) config))
        listener-name (or (:security.inter.broker.protocol parsed-config) "PLAINTEXT")
        uris (util/listener-uri (:listeners parsed-config) listener-name)
        props {:bootstrap.servers (first uris)}
        kafka-version (org.apache.kafka.common.utils.AppInfoParser/getVersion)]
    (reset! util/state {:kafka-version kafka-version
                   :kafka-config parsed-config
                   :admin-client (when (util/modern-kafka? kafka-version)
                                   (ka/admin-client props))
                   :consumer (ka/kafka-consumer props)})))

(defn -init [_ _]
  (let [config (:kafka-config @util/state)
        http-port (Integer/parseInt (or (:kafka_http_reporter.port config) 
                                        (:kafkahttpreporter.port config)
                                        "19092"))
        tcp-port (Integer/parseInt (or (:kafka_http_reporter.tcp_port config)
                                       (:kafkahttpreporter.tcp_port config)
                                       "19500"))]
    (println "[INFO] KafkaHttpReporter: Starting HTTP server on port " http-port )
    (swap! util/state assoc :http-server (http/start-server handler {:port http-port}))
    
    (println "[INFO] KafkaHttpReporter: Starting TCP server on port " tcp-port)
    (swap! util/state assoc :tcp-server (tcp/start-server (wrapper tcp-handler) {:port tcp-port}))))

(defn -metricChange [_ _])
(defn -metricRemoval [_ _])
(defn -close [_])
