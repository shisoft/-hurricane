(ns hurricane.interface
  (:require [cluster-connector.remote-function-invocation.core :as rfi]
            [cluster-connector.distributed-store.core :refer [join-cluster get-server-list]]
            [clojure.java.io :as io]
            [byte-streams]
            [hurricane.superviser.core :refer []])
  (:import [java.util.zip ZipEntry ZipOutputStream]
           (java.io FileInputStream File)))

(def app-id (atom nil))
(def worker-group (atom nil))

(defn hurr [func-symbol & params]
  (let [str-key (pr-str [symbol params])]
    (apply rfi/sharded-invoke-with-options str-key func-symbol {:region :hurricane :server-group @worker-group} params)))

(defn hmap [func-symbol coll]
  (pmap
    (fn [x]
      (hurr func-symbol x))
    coll))

(defn deploy-application [zk-addr & {:keys [region server-group service-port meta] :or {region :app server-group :app meta {}}}]
  (let [app-id- (or service-port (+ 1000 (rand-int 50000)))
        package-path (str (System/getProperty "java.io.tmpdir") app-id- ".zip")]
    (join-cluster region server-group app-id- zk-addr meta)
    (with-open [zip (ZipOutputStream. (io/output-stream package-path))]
      (doseq [f (file-seq (io/file "./")) :when (.isFile f)]
        (.putNextEntry zip (ZipEntry. (.getPath f)))
        (io/copy f zip)
        (.closeEntry zip)))
    (reset! app-id app-id-)
    (reset! worker-group (keyword (str "worker-" app-id-)))
    (let [proj-data (byte-streams/to-byte-array (File. package-path))
          server-names (get-server-list :superviser :region :hurricane)]
      (when (empty? server-names) (println "No Server To Deploy"))
      (doseq [server-name server-names]
        (println "Deploying" server-name)
        (rfi/invoke server-name 'hurricane.superviser.core/start-container app-id- proj-data)))
    (.addShutdownHook
      (Runtime/getRuntime)
      (Thread. (fn [] (doseq [server-name (get-server-list :superviser :region :hurricane)]
                        (println "Shutdown" server-name)
                        (rfi/invoke server-name 'hurricane.superviser.core/stop-container app-id-)))))))