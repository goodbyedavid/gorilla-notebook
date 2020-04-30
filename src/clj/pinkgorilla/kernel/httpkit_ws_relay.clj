(ns pinkgorilla.kernel.httpkit-ws-relay
  "A websocket handler that passes messages back and forth to an already running nREPL server."
  (:use [compojure.core :only (defroutes GET)]
        ring.util.response
        ring.middleware.cors
        ;org.httpkit.server
        )
  (:require 
   [org.httpkit.server :as http]
   [cheshire.core :refer :all]))

(def clients (atom {}))

(defn ws-handler
  [req]
  (http/with-channel req con
    (swap! clients assoc con true)
    (println con " nrepl ws relay: client connected!")
    (http/on-close con (fn [status]
                    (swap! clients dissoc con)
                    (println con " nrepl ws relay:disconnected. status: " status)))))

;(defn handler
;  [request]
;  (let [c (process request)] ;; long running process that returns a channel
;    (http/with-channel request channel
;      (http/send! channel {:status 200
;                           :body (<!! (go (<! c)))))
;      (http/on-close channel (fn [_] (async/close! c))))))


(future 
  (loop []
    (doseq [client @clients]
      (http/send! (key client) 
             (generate-string {:happiness (rand 10)})
             false))
    (Thread/sleep 10000)
    (recur)))
