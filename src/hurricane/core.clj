(ns hurricane.core
  (:gen-class))

(defn -main
  [& args]
  ())

(defn map-on-vals [f m]
  (into {}
        (map (fn [[k v]] [k (f v)]) m)))