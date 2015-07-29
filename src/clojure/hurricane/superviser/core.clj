(ns hurricane.superviser.core
  (:require [cluster-connector.remote-function-invocation.core :as rfi]
            [cluster-connector.distributed-store.core :as ds]
            [clojure.java.shell :refer [sh]]
            [clojure.core.async :refer [put! chan <!!]]
            [cluster-connector.sharding.core :refer [register-as-master]])
  (:import (hurricane.stream UnzipUtility)
           (java.io File)
           [java.lang Runtime]))

(def new-worker-awaits (atom {}))
(def worker-port-counter (atom 1900))
(def containers (atom {}))
(def use-docker? (atom false))

(defn next-worker-port []
  (locking worker-port-counter
    (swap! worker-port-counter inc)
    @worker-port-counter))

(defn start-server [zk-addr docker?]
  (reset! use-docker? docker?)
  (ds/join-cluster
    :hurricane :superviser 13999 zk-addr {}
    :connected-fn
    (fn [& _]
      (register-as-master)
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
      (println "Unpack Project")
      (UnzipUtility/writeBytesToFile proj package-path)
      (UnzipUtility/unzip package-path proj-path)
      (println "Starting Container")
      (let [start-cmd (if @use-docker?
                        ["docker" "run"
                         "--name" container-name
                         "-v" (str (System/getProperty "user.dir") ":" "/tmp")
                         "-v" (str proj-path ":" "/opt")
                         "-v" (str "/opt/.m2" ":" "/root/.m2")
                         "-p" (str worker-port ":" worker-port)
                         "-d"
                         "clojure"
                         "lein" "trampoline" "run"
                         "worker"
                         (str "--port=" worker-port)
                         (str "--zk="   @ds/zk-addr)
                         (str "--sn="   @ds/this-server-name)
                         (str "--wd="   "/opt")
                         (str "--id="   id)]
                        ["lein" "trampoline" "run"
                         "worker"
                         (str "--port=" worker-port)
                         (str "--zk="   @ds/zk-addr)
                         (str "--sn="   @ds/this-server-name)
                         (str "--wd="   proj-path)
                         (str "--id="   id)])]
        (println start-cmd)
        (future (. (Runtime/getRuntime) exec (into-array start-cmd))))
      (println "Container Started, waiting for startup")
      (let [worker-name (<!! await-chan)]
        (swap! new-worker-awaits dissoc worker-port)
        (swap! containers assoc id [container-name worker-port worker-name])
        (println "Cointainer Started")))
    (throw (Exception. "Container started, stop it first"))))

(defn stop-container [id]
  (let [[container-name worker-port worker-name] (@containers id)]
    (if @use-docker?
      (do (sh "docker" "stop" container-name)
          (sh "docker" "remove" container-name))
      (rfi/invoke worker-name 'hurricane.worker.core/end-worker))))

(defn new-worker-ready [port server-name]
  (if-let [worker-await-chan (@new-worker-awaits port)]
    (put! worker-await-chan  server-name)
    (println "Did not waiting for worker" port)) {})