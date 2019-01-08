(ns cloudkarafka.jmx
  (:require [clojure.java.jmx :as jmx]))

(defn jmx-values
  ([mbean attrs] (jmx-values mbean attrs nil))
  ([mbean attrs group]
   (try
     (let [value (jmx/mbean mbean)]
       (for [attr attrs
             :let [v (get value (keyword attr))
                   keys (into {} (map #(vector (keyword (.getKey %)) (.getValue %)) (.entrySet (.getKeyPropertyList mbean))))]]
         (if (map? v)
           (for [[k v] v]
             (assoc keys :key (name k) :value v :attribute attr))
           (assoc keys :value v :attribute attr))))
     (catch javax.management.InstanceNotFoundException e))))

(defn group-metrics
  [bean group attributes]
  (->> (jmx/mbean-names bean)
       (mapcat #(jmx-values % attributes group))
       (remove nil?)
       (group-by (keyword group))
       (map (fn [[k v]] (reduce #(assoc %1 :value (+ (:value %2) (:value %1))) v)))))

(defn query [bean group attributes]
  (cond group (group-metrics bean group attributes)
        (.contains bean "*") (mapcat #(jmx-values % attributes) (jmx/mbean-names bean))
        :else (flatten (jmx-values (jmx/as-object-name bean) attributes))))
