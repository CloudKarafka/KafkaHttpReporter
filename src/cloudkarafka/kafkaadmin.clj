(ns cloudkarafka.kafkaadmin
  (:require [cloudkarafka.util :as util]
            [t6.from-scala.core :as $]))

(defn map->props
  [m]
  (let [props (java.util.Properties.)]
    (doseq [[k v] m
            :when (and k v)]
      (.put props (name k) v))
    props))

(defn member-list [group-name desc group-offset log-offset]
  (for [member (.members desc)
        :let [ toppar (.topicPartitions (.assignment member)) ]]
        (if (empty? toppar)
          {:group group-name
           :topic nil,
           :partition nil,
           :current_offset nil,
           :log_end_offset nil,
           :lag nil,
           :clientid (.clientId member),
           :consumerid (.consumerId member),
           :host (.host member)}
          (for [tp toppar
                :let [group-partitions (get group-offset tp)
                      go (when group-partitions (.offset group-partitions))
                      lo (get log-offset tp)]]
            {:group group-name
             :topic (.topic tp)
             :partition (.partition tp)
             :current_offset go,
             :log_end_offset lo,
             :lag (when go (- lo go)),
             :clientid (.clientId member),
             :consumerid (.consumerId member),
             :host (.host member)}))))


(defn consumer-groups
  ([client consumer]
   (consumer-groups
    client
    consumer
    (into [] (map #(.groupId %) (.get (.all (.listConsumerGroups client)))))))
  ([client consumer group-ids]
   (let [descs (.get (.all (.describeConsumerGroups client group-ids)))]
     (flatten (for [group-id group-ids
                    :let [desc (get descs group-id)
                          group-offset (.get (.partitionsToOffsetAndMetadata (.listConsumerGroupOffsets client group-id)))
                          log-offset (.endOffsets consumer (mapv key group-offset))]]
                (member-list group-id desc group-offset log-offset))))))

(defn consumer-groups-old
  ([url consumer]
   (with-open [client (kafka.admin.AdminClient/createSimplePlaintext url)]
     (let [res (java.util.LinkedList.)]
       ($/for [group (.listAllConsumerGroupsFlattened client)
               :let [summary (.describeConsumerGroup client (.groupId group) 0)
                     group-offset (.listGroupOffsets client (.groupId group))]]
         ($/if-let [members (.consumers summary)]
           ($/for [member members
                   :let [log-end-offsets (.endOffsets consumer (scala.collection.JavaConversions/asJavaCollection (.assignment member)))]]
             ($/for [toppar (.assignment member)
                     :let [current-offset (get (scala.collection.JavaConversions/mapAsJavaMap group-offset) toppar)
                           log-end (get log-end-offsets toppar)]]
               (.add res {:state (.state summary)
                          :group (.groupId group)
                          :topic (.topic toppar)
                          :partition (.partition toppar)
                          :current_offset current-offset
                          :log_end_offset log-end
                          :lag (and log-end current-offset (- log-end current-offset))
                          :consumerid (.consumerId member)
                          :clientid (.clientId member)
                          :host (.host member)})))
           :none))
       res))))

(defn admin-client ^org.apache.kafka.clients.admin.AdminClient
  [props]
  (org.apache.kafka.clients.admin.AdminClient/create (map->props props)))

(defn kafka-consumer ^org.apache.kafka.clients.consumer.KafkaConsumer
  [props]
  (let [m (merge props
                 {:group.id "mgmt-admin",
                  :key.deserializer "org.apache.kafka.common.serialization.StringDeserializer",
                  :value.deserializer "org.apache.kafka.common.serialization.StringDeserializer"})
        props (map->props m)]
    (org.apache.kafka.clients.consumer.KafkaConsumer. props)))

(defn consumers
  ([]
   (let [s @util/state]
     (if (util/modern-kafka? (:kafka-version s))
       (consumer-groups (:admin-client s) (:consumer s))
       (let [plaintext-url (-> (:kafka-config s)
                               :listeners 
                               (util/listener-uri  "PLAINTEXT")
                               first)]
         (consumer-groups-old plaintext-url (:consumer s))))))
  ([group]
   (let [s @util/state]
     (if (util/modern-kafka? (:kafka-version s))
       (consumer-group (:admin-client s) (:consumer s) group)
       (let [plaintext-url (-> (:kafka-config s)
                               :listeners 
                               (util/listener-uri  "PLAINTEXT")
                               first)]
         (consumer-group-old plaintext-url (:consumer s) group))))))

(comment
  (consumer-groups-old
   "127.0.0.1:9092"
   (kafka-consumer {:bootstrap.servers "127.0.0.1:9092"}))

  (consumer-groups
   (admin-client {:bootstrap.servers "127.0.0.1:9092"})
   (kafka-consumer {:bootstrap.servers "127.0.0.1:9092"}))
)
