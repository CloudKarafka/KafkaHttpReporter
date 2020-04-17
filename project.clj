(defproject kafka-http-reporter "0.4.0"
  :description "Expose JMX metrics through a HTTP interface"
  :url "http://github.com/CloudKarafka/KafkaHttpReporter"
  :license {:name "Apache License 2.0"
            :url "https://github.com/CloudKarafka/KafkaHttpReporter/blob/master/LICENSE"}
  :profiles {:uberjar {:aot :all}
             :provided {:dependencies [[org.apache.kafka/kafka_2.11 "1.1.0"]
                                       [org.apache.kafka/kafka-clients "1.1.0"]]}}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.jmx "1.0.0"]
                 [metosin/jsonista "0.2.5"]
                 [aleph "0.4.6"]
                 [manifold "0.1.8"]
                 [compojure "1.6.1"]
                 [t6/from-scala "0.3.0"]])
