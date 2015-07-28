(defproject hurricane "0.1.0-SNAPSHOT"
  :description "Distributed Symbonic Computing System"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cluster-connector "0.1.0-SNAPSHOT"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [leiningen-core "2.5.1"]
                 [byte-streams "0.2.0"]]
  :main ^:skip-aot hurricane.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"])
