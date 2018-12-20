(ns cloudkarafka.kafkaadmin
  (:import org.apache.kafka.clients.consumer.KafkaConsumer
           org.apache.kafka.clients.admin.AdminClient
           org.apache.kafka.clients.consumer.internals.ConsumerProtocol))

(defn map->props
  [m]
  (let [props (java.util.Properties.)]
    (doseq [[k v] m] (.put props (name k) v))
    props))

(defn- map->msgs
  [group topic partition d]
  (for [[k v] d
        :when (not (nil? v))]
    {:topic topic,
     :partition partition,
     :name k
     :value v,
     :group group}))

(defn member-list [desc group-offset log-offset]
  (for [member (.members desc)
        toppar (.topicPartitions (.assignment member))
        :let [group-partitions (get group-offset toppar)
              go (when group-partitions (.offset group-partitions))
              lo (get log-offset toppar)]]
    {:topic (.topic toppar)
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
    (into {} (for [group-id group-ids
                   :let [desc (get descs group-id)
                         group-offset (.get (.partitionsToOffsetAndMetadata (.listConsumerGroupOffsets client group-id)))
                         log-offset (.endOffsets consumer (mapv key group-offset))]]
               (vector group-id (member-list desc group-offset log-offset))))))

(defn admin-client ^AdminClient
  [props]
  (AdminClient/create (map->props props)))

(defn kafka-consumer ^KafkaConsumer
  [props]
  (let [m (merge props
                 {:group.id "mgmt-admin",
                  :key.deserializer "org.apache.kafka.common.serialization.StringDeserializer",
                  :value.deserializer "org.apache.kafka.common.serialization.StringDeserializer"})
        props (map->props m)]
    (KafkaConsumer. props)))

(comment
  (def props {:bootstrap.servers "127.0.0.1:9092"})
  (consumer-groups
   (admin-client props)
   (kafka-consumer props))
  
  )
