(ns hurricane.core
  (:gen-class))

(defn -main
                              "I don't do a whole lot ... yet."
                              [& args]
                              (println "Hello, World!"))

(defn map-on-vals [f m]
  (into {}
        (map (fn [[k v]] [k (f v)]) m)))