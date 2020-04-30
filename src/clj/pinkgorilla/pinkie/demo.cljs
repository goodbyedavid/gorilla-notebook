(ns pinkgorilla.pinkie.demo
  (:require
    [pinkgorilla.notebook.core :refer [empty-notebook
                                       insert-segment-at remove-segment
                                       create-code-segment create-free-segment]] ; manipulate notebook
))

(defn console! [segment output]
  (assoc-in segment [:console-response] output))


(defn value-html! [segment content]
  (assoc-in segment [:value-response] {:type :html
                                       :content content}))

(defn value-reagent! [segment hiccup]
  (assoc-in segment [:value-response] {:type :reagent
                                       :content {:hiccup hiccup
                                                 :map-keywords true
                                                 :widget true}}))

(defn create-dummy-notebook []
  (let [worksheet (empty-notebook)
        md-howto (create-free-segment "# Pinkie Renderer \n\nRender visuals from clj.")
        code-add (-> (create-code-segment "(+ 1 1 )")
                     (console! "the calculation was performed")
                     (value-html! [:span.clj-long 2]))
        code-vec (-> (create-code-segment "[ 1 2 3]")
                     (value-html! [:span.clj-long "[ 1 2 3]"]))
        code-h   (-> (create-code-segment "^:R [:h1 \"hello world\"] ")
                     (value-reagent! [:h1 "hello world!"]))
        code-r   (-> (create-code-segment "^:R [:json {:a 1 :b 2}]")
                     (value-reagent! [:json {:a 1 :b 2}]))]
    (-> worksheet
        (insert-segment-at 0 md-howto)
        (insert-segment-at 1 code-add)
        (insert-segment-at 2 code-vec)
        (insert-segment-at 3 code-h)
        (insert-segment-at 4 code-r)
        )))

