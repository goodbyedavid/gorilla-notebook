(ns pinkgorilla.events
  (:require
   [day8.re-frame.http-fx]
   [day8.re-frame.undo :as undo]

   ["mousetrap"]
   ["mousetrap-global-bind"]

   ;; event requires produce side effects (they register the event handlers)
   ;; [pinkgorilla.events.usage-analytics]
   [pinkgorilla.events.common :refer [reg-set-attr]]

   [pinkgorilla.events.config]
   [pinkgorilla.events.message]
   [pinkgorilla.events.notifications]
   [pinkgorilla.events.palette]
   [pinkgorilla.events.views] ; main component in the browser / navbar
   [pinkgorilla.events.auth]  
   [pinkgorilla.events.domain] 
   [pinkgorilla.events.auth2]

   [pinkgorilla.events.settings]
   [pinkgorilla.events.notebook]
   [pinkgorilla.events.storage]
   [pinkgorilla.events.storage-save-dialog]
   [pinkgorilla.events.storage-file]

   [pinkgorilla.events.kernel]
   [pinkgorilla.events.kernel-toggle]
   [pinkgorilla.events.kernel-docstring]

   ; pinkgorilla ui
   
   [pinkgorilla.explore.subscriptions]
   [pinkgorilla.explore.events]))

(reg-set-attr ::set-navbar-menu-active? :navbar-menu-active?)


;; TODO Should move evaluation state out of worksheet


(undo/undo-config! {:max-undos    3
                    :harvest-fn   (fn [ratom] (some-> @ratom :worksheet))
                    :reinstate-fn (fn [ratom value] (swap! ratom assoc-in [:worksheet] value))})
