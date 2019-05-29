(defproject kafka-http-reporter "0.3.3"
  :description "Expose JMX metrics through a HTTP interface"
  :url "http://github.com/CloudKarafka/kafka-http-reporter"
  :license {:name "Apache License 2.0"
            :url "https://github.com/CloudKarafka/KafkaHttpReporter/blob/master/LICENSE"}
  :profiles {:uberjar {:aot :all}
             :provided {:dependencies [[org.apache.kafka/kafka_2.11 "1.1.0"]
                                       [org.apache.kafka/kafka-clients "1.1.0"]]}}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/java.jmx "0.3.4"]
                 [metosin/jsonista "0.2.2"]
                 [aleph "0.4.6"]
                 [compojure "1.6.1"]
                 [t6/from-scala "0.3.0"]])
