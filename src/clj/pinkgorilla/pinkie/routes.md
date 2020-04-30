(ns pinkie.pinkie.routes
  (:require
   [clojure.core.async :as async  :refer (<! <!! >! >!! put! chan go go-loop)]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.middleware.gzip :refer [wrap-gzip]]
   [ring.middleware.keyword-params]
   [ring.middleware.params]
;   [ring.middleware.cljsjs :refer [wrap-cljsjs]]
  ; [ring.middleware.anti-forgery :as af :refer :all]
   [ring.middleware.anti-forgery :refer (*anti-forgery-token*)]
   [ring.util.response :as response]
   [compojure.core :as comp :refer (defroutes GET POST)]
   [compojure.route :as route]
   [aleph.http :as aleph]

   [taoensso.encore :as encore :refer (have have?)]
   [taoensso.timbre :as log :refer (tracef debugf infof warnf errorf)]
   [taoensso.sente  :as sente]
   [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
   [taoensso.sente.packers.transit :as sente-transit]

   [cheshire.core :as json]))

(defn unique-id
  "Get a unique id for a session."
  []
  (str (java.util.UUID/randomUUID)))

(defn session-uid
  "Get session uuid from a request."
  [req]
  (get-in req [:session :uid]))


;(log/set-level! :info)
;; (log/set-level! :debug)
(taoensso.timbre/set-level! :trace) ; Uncomment for more logging


(reset! sente/debug-mode?_ true) ; Uncomment for extra debug info

; packer :edn

(let [packer (sente-transit/get-transit-packer)
      chsk-server (sente/make-channel-socket-server!
                   (get-sch-adapter)
                   {:packer packer
                    :csrf-token-fn nil ; awb99; disable CSRF checking.
                    })
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]} chsk-server]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defn send-all!
  [data]
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid data)))

(add-watch connected-uids :connected-uids
           (fn [_ _ old new]
             (when (not= old new)
               (infof "Connected uids change: %s" new))))

(defn connected-uids? []
  @connected-uids)

(defroutes pinkie-routes
  (GET  "/pinkie" req (response/content-type
                 {:status 200
                  :session (if (session-uid req)
                             (:session req)
                             (assoc (:session req) :uid (unique-id)))
                  :body (io/input-stream (io/resource "gorilla-repl-client/pinkie.html"))}
                 "text/html"))

  (GET "/token" req (json/generate-string {:csrf-token *anti-forgery-token*}))

  (GET  "/chsk" req
    (debugf "/chsk got: %s" req)
    (let [r (ring-ajax-get-or-ws-handshake req)]
      (println "ws init: " r)
      (println "token: " (get-in req [:session :ring.middleware.anti-forgery/anti-forgery-token]))
      r))

  (POST "/chsk" req (ring-ajax-post req)))