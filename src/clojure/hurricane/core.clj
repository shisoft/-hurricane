(ns hurricane.core
  (:gen-class)
  (:require [hurricane.superviser.core :as superviser]
            [hurricane.worker.core :as worker]
            [cluster-connector.utils.for-debug :refer [$ spy]]))

(defn in-args [args & s]
  (let [params
        (apply hash-map
               (mapcat
                 #(mapcat
                   (fn [s]
                     (let [[attr v] (clojure.string/split s #"=")]
                       (if v [attr v] [attr true])))
                   (if (= "--" (subs % 0 2)) [%]
                                             (if (= "-" (subs % 0 1))
                                               (map (partial str "-") (rest %))
                                               (throw (Exception. "invalid args")))))
                 args))]
    (first (filter identity (map params s)))))

(defn -main [& args]
  "Start Hurricane Engine"
  (if args
    (let [in-args (partial in-args (rest args))
          zk-addr (or (in-args "--zk") (System/getenv "ZK"))
          superviser-name (in-args "--sn")
          port (#(when % (Integer/parseInt %)) (in-args "--port"))
          id   (in-args "--id")
          wd   (in-args "--wd")
          docker (in-args "--docker")]
      (println "Shisoft Hurricane")
      (case (keyword (first args))
        :superviser
        (superviser/start-server zk-addr docker)
        :worker
        (worker/start-server id port zk-addr superviser-name wd)
        (println (str "Don't know what to do with " args))))))