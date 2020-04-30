(ns pinkgorilla.notebook-app.servlet
  (:require
   [taoensso.timbre :refer [debug info]]
   [ring.util.servlet :as servlet]
   [pinkgorilla.notebook-app.route :as route])
  (:import (javax.servlet ServletConfig)
           (javax.servlet.http HttpServletRequest HttpServletResponse))
  (:gen-class :name pinkgorilla.RingServlet
              :extends javax.servlet.http.HttpServlet
              :exposes-methods {init superInit}))

(def ring-service (atom nil))

;; (servlet/set-handler (gorilla/get-handlers))
(defn set-handler
  [handler]
  (info "Creating new service method from handler " handler)
  (reset! ring-service (servlet/make-service-method handler)))

(defn -init
  ([this]
   (debug "Servlet initialized with no params")
   (.superInit this)
   (set-handler
    (route/war-handler (-> (.getServletContext this) .getContextPath))))

  ([this ^ServletConfig config]
   (debug "Servlet initialized with servlet config" config)
   (.superInit this config)
   (set-handler
    (route/war-handler (-> (.getServletContext this) .getContextPath)))))

(defn -service
  [this ^HttpServletRequest request ^HttpServletResponse response]
  (@ring-service this request response))

;; (defn -destroy
;;  [this]
;;  (log/debug "Servlet destroyed"))

