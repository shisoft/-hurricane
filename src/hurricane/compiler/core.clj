(ns hurricane.compiler.core
  (:require [clojure.walk :as walk]))

(def symbols (read-string (slurp "conf/symbols.edn")))

(defn hurr-func [exp]
  (symbol (str "hurricane.compiler.funcs/" (name (get-in symbols [exp :replacement])))))

(defmacro hurricane [[data-source & opts] & body]
  `(do ~@(map
           (fn [line]
             (walk/prewalk
               (fn [exp]
                 (if (get symbols exp)
                   (hurr-func exp)
                   exp))
               line))
           body)))

(defmacro hmap []
  )