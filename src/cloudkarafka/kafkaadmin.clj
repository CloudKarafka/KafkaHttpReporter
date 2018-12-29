(ns cloudkarafka.kafkaadmin
  (:require [t6.from-scala.core :as $]))

(defn map->props
  [m]
  (let [props (java.util.Properties.)]
    (doseq [[k v] m] (.put props (name k) v))
    props))

(defn member-list [group-name desc group-offset log-offset]
  (for [member (.members desc)
        toppar (.topicPartitions (.assignment member))
        :let [group-partitions (get group-offset toppar)
              go (when group-partitions (.offset group-partitions))
              lo (get log-offset toppar)]]
    {:group group-name
     :topic (.topic toppar)
     :partition (.partition toppar)
     :current_offset go,
     :log_end_offset lo,
     :lag (when go (- lo go)),
     :clientid (.clientId member),
     :consumerid (.consumerId member),
     :host (.host member)}))

(defn consumer-groups
  [client consumer]
  (let [group-ids (into [] (map #(.groupId %) (.get (.all (.listConsumerGroups client)))))
        descs (.get (.all (.describeConsumerGroups client group-ids)))]
    (flatten (for [group-id group-ids
                   :let [desc (get descs group-id)
                         group-offset (.get (.partitionsToOffsetAndMetadata (.listConsumerGroupOffsets client group-id)))
                         log-offset (.endOffsets consumer (mapv key group-offset))]]
               (member-list group-id desc group-offset log-offset)))))

(defn consumer-groups-old
  [client consumer]
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
    res))

(defn admin-client-old ^kafka.admin.AdminClient
  [props]
  (kafka.admin.AdminClient/createSimplePlaintext (:bootstrap.servers props)))

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

(comment

  (consumer-groups-old (admin-client-old {:bootstrap.servers "127.0.0.1:9092"}) (kafka-consumer {:bootstrap.servers "127.0.0.1:9092"}))

  )
