(ns cloudkarafka.fast-jmx
  (:require [clojure.java.jmx :as jmx]
            [clojure.string :as str])
  (:import [java.io Reader Writer]
           [javax.management ObjectName]
           [java.util Map$Entry]))

(set! *warn-on-reflection* true)

(defn write-err [str]
  (let [^Writer o *err*]
    (.write o (format "%s\n" str))
    (.flush o)))

(defn parse-line [line]
  (let [t (str/trim line)
        [cmd bean] (str/split t #" ")]
    [cmd bean]))

(defn parse-host [str]
  (let [[host port] (str/split str #":")]
    {:host host
     :port (Integer/parseInt port)}))

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

(defn bean-value [mbean]
  (try
    (jmx/mbean mbean)
    (catch javax.management.OperationsException e
      (write-err (.getMessage e))
      {})))

(defn jmx-values [mbean]
  (let [params (mbean-params mbean)
        values (build-value-map "" (jmx/mbean mbean))]
    (merge params values)))

(defn query [^String bean]
  (cond (.contains bean "*") (doall (map jmx-values (jmx/mbean-names bean)))
        :else (list (jmx-values (jmx/as-object-name bean)))))

(defn ^String format-result [values]
  (->> values
       (map (fn [[k v]] (str k "=" v)))
       (str/join ";;")))

(defmulti exec (fn [a _] a))

(defmethod exec "jmx" [_ bean]
  (query bean))

(defmethod exec "v" [_ bean]
  (list {bean "2.3.1"}))

(defmethod exec :default [_ _]
  (list {}))

(defn handler [^Reader in ^Writer out]
  (doseq [ln (line-seq (java.io.BufferedReader. in))]
    (let [[cmd bean] (parse-line ln)]
      (when-not (empty? cmd)
        (doseq [res (exec cmd bean)
                :when (seq res)]
          (.write out (format-result res))
          (.write out "\n")))
      (.write out "\n")
      (.flush out))))
