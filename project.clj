(defproject kafka-http-reporter "0.1.0"
  :description "Expose JMX metrics through a HTTP interface"
  :url "http://github.com/CloudKarafka/kafka-http-reporter"
  :license {:name "MIT"
            :url ""}
  :profiles {:uberjar {:aot :all}}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/java.jmx "0.3.4"]
                 [org.apache.kafka/kafka-clients "2.1.0"]
                 [metosin/jsonista "0.2.2"]
                 [aleph "0.4.6"]
                 [compojure "1.6.1"]])
