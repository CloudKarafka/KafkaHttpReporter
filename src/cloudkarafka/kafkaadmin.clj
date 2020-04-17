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

(defn member-list [group-name desc group-offset]
  (for [member (.members desc)
        :let [ toppar (.topicPartitions (.assignment member)) ]]
        (if (empty? toppar)
          {:group group-name
           :clientid (.clientId member),
           :consumerid (.consumerId member),
           :host (.host member)}
          (for [tp toppar
                :let [group-partitions (get group-offset tp)
                      go (when group-partitions (.offset group-partitions))]]
            {:group group-name
             :topic (.topic tp)
             :partition (.partition tp)
             :current_offset go,
             :clientid (.clientId member),
             :consumerid (.consumerId member),
             :host (.host member)}))))

(defn consumer-groups
  ([client]
   (consumer-groups client nil))
  ([client group-ids]
   (let [group-ids (or group-ids
                       (into [] (map #(.groupId %) (.get (.all (.listConsumerGroups client))))))
         descs (.get (.all (.describeConsumerGroups client group-ids)))]
     (flatten (for [group-id group-ids
                    :let [desc (get descs group-id)
                          group-offset (.get (.partitionsToOffsetAndMetadata (.listConsumerGroupOffsets client group-id)))]]
                (member-list group-id desc group-offset))))))

(defn filtered-groups [client group-ids]
  (let [all-group-ids (map #(.groupId %) (.listAllConsumerGroupsFlattened client))
        wanted (set group-ids)]
    (if (empty? wanted)
      all-group-ids
      (filter #(contains? wanted %) all-group-ids))))

(defn consumer-groups-old
  ([url group-ids]
   (with-open [client (kafka.admin.AdminClient/createSimplePlaintext url)]
     (let [res (java.util.LinkedList.)]
       ($/for [group-id (filtered-groups client group-ids)
               :let [summary (.describeConsumerGroup client group-id 0)
                     group-offset (.listGroupOffsets client group-id)]]
         ($/if-let [members (.consumers summary)]
           ($/for [member members]
             ($/for [toppar (.assignment member)
                     :let [current-offset (get (scala.collection.JavaConversions/mapAsJavaMap group-offset) toppar)]]
               (.add res {:state (.state summary)
                          :group group-id
                          :topic (.topic toppar)
                          :partition (.partition toppar)
                          :current_offset current-offset
                          :consumerid (.consumerId member)
                          :clientid (.clientId member)
                          :host (.host member)})))
           :none))
       res))))

(defn admin-client ^org.apache.kafka.clients.admin.AdminClient
  [props]
  (org.apache.kafka.clients.admin.AdminClient/create (map->props props)))

(defn consumers
  ([] (consumers nil))
  ([groups]
   (let [s @util/state
         kafka-config (:kafka-config s)]
     (if (util/modern-kafka?)
       (consumer-groups (:admin-client s) groups)
       (let [plaintext-url (-> kafka-config
                               :listeners 
                               (util/listener-uri
                                (or (:security.inter.broker.protocol kafka-config) "PLAINTEXT"))
                               first)]
         (consumer-groups-old plaintext-url (:consumer s) groups))))))

