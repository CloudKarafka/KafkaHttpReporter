(ns cloudkarafka.cmds
  (:require [clojure.java.jmx :as jmx]
            [cloudkarafka.kafkaadmin :as ka]
            [clojure.string :as str])
  (:import [javax.management ObjectName]
           [java.util Map$Entry]))

(defn- mbean-keys->map [^Map$Entry e]
  (vector (.getKey e) (.getValue e)))

(defn valid-value? [v]
  (or
   (string? v)
   (int? v)
   (double? v)
   (boolean? v)))

(defn build-value-map
  ([m] (build-value-map "" m))
  ([p m]
   (reduce
    (fn [res [k v]]
      (if (map? v)
        (merge res (build-value-map (str p (name k) ".") v))
        (if (valid-value? v)
          (assoc res (str p (name k)) v)
          res)))
    {}
    m)))

(defn mbean-params [^ObjectName mbean]
  (let [kpl (.getKeyPropertyList mbean)
        entries (.entrySet kpl)]
    (into {} (map mbean-keys->map entries))))

(defn jmx-values [mbean]
  (let [params (mbean-params mbean)
        values (build-value-map "" (jmx/mbean mbean))]
    (merge params values)))

(defn query [^String bean]
  (cond (.contains bean "*") (doall (map jmx-values (jmx/mbean-names bean)))
        :else (list (jmx-values (jmx/as-object-name bean)))))

(defn format-result-row [values]
  (->> values
       (map (fn [[k v]] (str (name k) "=" v)))
       (str/join ";;")))

(defn format-result [values]
  (str
   (str/join "\n" (map format-result-row values))
   "\n"))

(defmulti exec (fn [a _] a))

(defmethod exec "jmx" [_ bean]
  (try
    (when bean
      (query bean))
    (catch javax.management.OperationsException e
      (println e))))

(defmethod exec "v" [_ bean]
  (case bean
    "kafka" (list {"kafka" (org.apache.kafka.common.utils.AppInfoParser/getVersion)})
    :else nil))

(defmethod exec "groups" [_ group]
  (ka/consumers group))

(defmethod exec :default [_ _]
  nil)


