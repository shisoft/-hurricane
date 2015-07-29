(ns hurricane.worker.core
  (:require [leiningen.core.classpath :as classpath]
            [cemerick.pomegranate :as pomegranate]
            [cluster-connector.remote-function-invocation.core :as rfi]
            [cluster-connector.distributed-store.core :refer [join-cluster this-server-name]]
            [hurricane.superviser.core]
            [cluster-connector.utils.for-debug :refer [spy $]]
            [cluster-connector.sharding.core :refer [register-as-master]]))

(defn eval-in [project form]
  (doseq [path (classpath/get-classpath project)]
    (pomegranate/add-classpath path))
  (spy form)
  (eval form))

(defn start-server [id port zk-addr superviser-name wd]
  (join-cluster
    :hurricane (keyword (str "worker-" id)) port zk-addr {}
    :connected-fn
    (fn [& _]
      ($ register-as-master)
      ($ rfi/start-server port))
    :expired-fn
    (fn [& _]
      (rfi/stop-server)))
  (let [project (read-string (slurp (str wd "/project.clj")))
        project (-> (apply hash-map (subvec (vec project) 3))
                    (assoc :eval-in :classloader
                           :source-paths [(str wd "/src")]))
        core-namespace (:hurricane-entrance project)]
    (eval-in
      project `(require (symbol ~(str core-namespace)))))
  (rfi/invoke superviser-name 'hurricane.superviser.core/new-worker-ready port @this-server-name))

(defn end-worker []
  (System/exit 0))