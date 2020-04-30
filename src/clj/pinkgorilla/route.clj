(ns pinkgorilla.route
  "web route handlers
   the actual loading of the routes is handled via tesla.
   tesla gets the handler as a string (namespace/function)

   websockets are handled via jetty config.

  "
  
  (:require
   [taoensso.timbre :refer [info]]
   [compojure.core :as compojure :refer [defroutes routes context GET POST]]
   [compojure.route :refer [files resources not-found] :as compojure-route]
   [selmer.parser :as sel]
   [ring.middleware.session :refer [wrap-session]]
   ;;PinkGorilla Libraries
   [pinkgorilla.middleware.cider :as mw-cider]
   [pinkgorilla.ui.hiccup_renderer :as renderer]   ; this is needed to bring the render implementations into scope
   ;;Pinkorilla Notebook   
   [pinkgorilla.notebook-app.wrap :refer [wrap-api-handler wrap-cors-handler redirect-app config get-war-prefix]]
   [pinkgorilla.storage.storage-handler :refer [save-notebook load-notebook]]
   [pinkgorilla.explore.explore-handler :refer [gorilla-files req-explore-directories-async]]))

(defroutes api-handlers
  (GET "/load" [] load-notebook)
  (POST "/save" [] save-notebook)
  (GET "/gorilla-files" [] gorilla-files)
  (GET "/explore" [] req-explore-directories-async)
  (GET "/config" [] config))

(defn document-utf8
  [filename req]
  {:status  200
   ;; utf-8 needed HERE, content sets ISO-8859-1 default which
   ;; supercedes meta header in document
   ;; Session key is required to force setting the cookie
   :session (:session req)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (sel/render-file (str "gorilla-repl-client/" filename)
                             {:name "Pink Gorilla"})})

(defroutes resource-handlers
  (resources "/")  ;; Needed during development
   ;; ^{:name "res2"} (compojure-route/resources "/" {:root "public"}) ; Yuck, seems to be required for uberwar
   ; (wrap-webjars handler "/webjars") ;; TODO Fix this
   ;; ^{:name ":res2"} (compojure-route/resources "/" {:root "META-INF/resources/"}) ;; webjars servlet 3 style
  (GET ":document.html" [document]
    (partial document-utf8 (str document ".html")))
  (resources "/" {:root "gorilla-repl-client"})
  (files "/project-files" {:root "."})
  (not-found "Bummer, not found"))

;; DEFAULT HANDLER

(defroutes default-routes
  (-> api-handlers
      (wrap-api-handler)
      (wrap-cors-handler))
  resource-handlers)

(def default-handler
  (wrap-session default-routes))

; NREPL

#_(defn create-repl-handlers
    [prefix receive-fn]
    [(GET (str prefix "repl") [] (ws-relay/jetty-repl-ring-handler receive-fn))])

(def nrepl-handler (atom (mw-cider/cider-handler) #_(cljs/cljs-handler)))

#_(def default-repl-handlers (create-repl-handlers "/" (partial ws-relay/on-receive-mem nrepl-handler)))
#_(def remote-repl-handlers (create-repl-handlers "/" ws-relay/on-receive-net))

(def remote-repl-handler
  "TODO: Implement this - 
   but beware that (web) resources from extension jars don't work with a remote repl"
  default-routes)

; UBER-WAR

(defn prefix-handler
  [prefix handler]
  (info "route prefix: " prefix)
  (context prefix [] handler))

;; Used by uberwar (Not sure whether it works as of Jan 2020)
(def redirect-handler
  (GET "/" [] redirect-app))

(defn war-handler [prefix]
  (prefix-handler (str prefix (get-war-prefix)) default-routes))

