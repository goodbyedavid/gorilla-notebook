(ns pinkgorilla.dev-handle
  (:require
   ;[compojure.core :as compojure]
   ;; [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
   [prone.middleware :refer [wrap-exceptions]]
   [ring.middleware.reload :refer [wrap-reload]]
   [pinkgorilla.route :refer [default-routes]]))

(defn wrap-dev [handler]
  (-> handler
      ;; (wrap-defaults api-defaults)
      wrap-exceptions
      wrap-reload))

(def dev-handler
  (wrap-dev default-routes))


