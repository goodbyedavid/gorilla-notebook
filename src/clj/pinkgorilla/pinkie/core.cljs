(ns pinkgorilla.pinkie.core
  "entry point to pinkie renderer (that works with notespace)"
  (:require
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   ; pinkgorilla libraries   
   [pinkgorilla.pinkie.events] ; add pinkie event handlers to reframe
   [pinkgorilla.pinkie.ws :refer [send! start-router!]]))

(timbre/set-level! :debug)
;(timbre/set-level! :info)
(enable-console-print!)

(defn- log [a-thing]
  (.log js/console a-thing))

;; CSRF check

(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))

;; see: https://github.com/ptaoussanis/sente/blob/master/example-project/src/example/client.cljs

(defn sente-csrf-warning []
  (if ?csrf-token
    (println "CSRF token detected in HTML, great!")
    (println "CSRF token NOT detected in HTML, default Sente config will reject requests")))


(defn ^:export pinkie-start! []
  (println "pinkie-start!")
  (sente-csrf-warning)
  (start-router!)
  (send! [:pinkie/a {:a 12}]))


  

