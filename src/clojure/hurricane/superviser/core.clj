(ns hurricane.superviser.core
  (:require [hurricane.superviser.caches :refer [pending-reqs]]
            [leiningen.core.classpath :as classpath]
            [cluster-connector.remote-function-invocation.core :as rfi]
            [cluster-connector.distributed-store.core :as ds]
            [clojure.java.shell :refer [sh]]
            [clojure.core.async :refer [put! chan <!!]])
  (:import (hurricane.stream UnzipUtility)
           (java.io File)))

(def cluster-conf (read-string (slurp "conf/cluster.edn")))
(def new-worker-awaits (atom {}))
(def worker-port-counter (atom {}))
(def containers (atom {}))

(defn next-worker-port []
  (locking worker-port-counter
    (swap! worker-port-counter inc)
    @worker-port-counter))

(defn start-server [zk-addr]
  (ds/join-cluster
    :hurricane :superviser 13999 (or zk-addr (:zk cluster-conf)) {}
    :connected-fn
    (fn [& _]
      (rfi/start-server 13999))
    :expired-fn
    (fn [& _]
      (rfi/stop-server))))

(defn start-container [id proj]
  (if-not (@containers id)
    (let [worker-port (next-worker-port)
          container-name (str "hurricane-worker-" worker-port)
          await-chan (chan 1)
          package-path (str (System/getProperty "java.io.tmpdir") id ".zip")
          proj-path    (str (System/getProperty "java.io.tmpdir") id ".proj")]
      (swap! new-worker-awaits assoc worker-port await-chan)
      (.mkdirs (File. proj-path))
      (UnzipUtility/writeBytesToFile proj package-path)
      (UnzipUtility/unzip package-path proj-path)
      (sh "nohup"
          "lein" "trampoline" "run"
          "worker"
          (str "--port=" worker-port)
          (str "--zk="   @ds/zk-addr)
          (str "--sn="   @ds/this-server-name)
          (str "--wd="   proj-path)
          (str "--id="   id))
      (let [worker-name (<!! await-chan)]
        (swap! new-worker-awaits dissoc worker-port)
        (swap! containers assoc id [container-name worker-port worker-name])))
    (throw (Exception. "Container started, stop it first"))))

(defn stop-container [id]
  (let [[container-name worker-port worker-name] (@containers id)]
    ;(sh "docker" "stop" container-name)
    ;(sh "docker" "remove" container-name)
    (rfi/invoke worker-name 'hurricane.worker.core/end-worker)))

(defn new-worker-ready [port server-name]
  (put! (@new-worker-awaits port) server-name))