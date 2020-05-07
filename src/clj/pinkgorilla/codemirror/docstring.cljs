(ns pinkgorilla.codemirror.docstring
  (:require
   [clojure.string :as str]
   [reagent.core :as reagent]
   [reagent.dom]
   [re-frame.core :as rf :refer [subscribe]]
   [goog.dom :as gdom]
   [dommy.core :as dom]
   [pinkgorilla.subs :as s]
))

(defn doc-viewer
  []
  (let [docs (subscribe [:docs])]
    (reagent/create-class
     {
      :display-name         "doc-viewer"  ;; for more helpful warnings & errors
      :component-did-update (fn [this _] ; old_argv
                               ;; TODO Ugly, but a bit more tricky to get right in reagent-render/render
                              (if-let [hint-el (gdom/getElementByClass "CodeMirror-hints")]
                                (let [rect (.getBoundingClientRect hint-el)
                                      el (reagent.dom/dom-node this)
                                      top (.-top rect)
                                      right (.-right rect)]
                                  (dom/set-px! el :top top :left right)
                                   ;;
                                   ;; (set! (.-top el-style) top)
                                   ;; (set! (.-left el-style) right)
                                  )))
      :reagent-render
      (fn []
        [:div.DocViewer.doc-viewer {:style (if (str/blank? (:content @docs))
                                             {:display "none"} {})}
         [:div.DocViewer.doc-viewer-content {}
          [:div {:dangerouslySetInnerHTML {:__html (:content @docs)}}]]])})))
