(ns pinkgorilla.pinkie.dsl
  (:require
   [pinkgorilla.ui.gorilla-renderable :refer [render-renderable-meta]]
   [pinkgorilla.pinkie.core :refer [send-all!]]))

(defn code-add
  "sends to browser instruction to set the code for an evaluation"
  [id code]
  (let [id-as-keyword (if (keyword? id) id (keyword (str id)))]
    (send-all!
     [:pinkie/code-add {:id id-as-keyword :code code}])))

(defn result-set
  "sends to browser instruction to set the result of an evaluation"
  [id result]
  (let [id-as-keyword (if (keyword? id) id (keyword (str id)))
        result-rendered (render-renderable-meta result)]
    (send-all!
     [:pinkie/result-set {:id id-as-keyword :result result-rendered}])))
