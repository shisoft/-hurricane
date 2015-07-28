(ns hurricane.worker.core
  (:require [leiningen.core.classpath :as classpath]
            [cemerick.pomegranate :as pomegranate]
            [cluster-connector.remote-function-invocation.core :as rfi]
            [cluster-connector.distributed-store.core :refer [join-cluster this-server-name]]))

(def cluster-conf (read-string (slurp "conf/cluster.edn")))

(defn eval-in [project core-namespace]
  (doseq [path (classpath/get-classpath project)]
    (pomegranate/add-classpath path))
  (eval '(require ~core-namespace)))

(defn start-server [id port zk-addr superviser-name wd]
  (join-cluster
    :hurricane (keyword (str "worker-" id)) port (or zk-addr cluster-conf) {}
    :connected-fn
    (fn [& _]
      (rfi/start-server port))
    :expired-fn
    (fn [& _]
      (rfi/stop-server)))
  (let [project (read-string (slurp (str wd "/project.clj")))
        project (-> (apply hash-map (subvec (vec project) 3))
                    (assoc :eval-in :classloader
                           :source-paths [(str wd "/src")]))]
    (eval-in
      project `(require (symbol ~(:hurricane-entrance project)) :reload-all)))
  (rfi/invoke superviser-name 'hurricane.superviser.core/new-worker-ready port @this-server-name))

(defn end-worker []
  (System/exit 0))