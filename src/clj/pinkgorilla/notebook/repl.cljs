(ns pinkgorilla.notebook.repl
  "important stuff that will be needed by notebook users.
   it should be easy to find this functions, so they are centrally managed.
  "
  (:require
   [clojure.string :as str]
   [goog.string :as gstring]
   [re-frame.core :refer [subscribe]]
   ;[taoensso.timbre :refer-macros (info)]
   [shadow.cljs.bootstrap.env]
   [pinkgorilla.ui.gorilla-renderable :refer [Renderable render]]
   [pinkgorilla.ui.pinkie]
   [pinkgorilla.kernel.nrepl :refer [clj-eval-cb]]))


; make register-tag available in the repl namespace


(def register-tag pinkgorilla.ui.pinkie/register-tag)

(defn r!
  "renders a (hydrated) reagent component"
  [vec_or_reagent_f]
  (reify Renderable
    (render [_]
      {:type :reagent
       :content {:hiccup vec_or_reagent_f
                 :map-keywords true}
             ;:value result
       })))

(defn loaded-shadow-namespaces []
  (let [lns  @shadow.cljs.bootstrap.env/loaded-ref
        lns (map name lns)
        lns (remove #(str/starts-with? % "goog") lns)
        lns (remove #(str/starts-with? % "devtools") lns)
        lns (remove #(str/starts-with? % "module$node_modules") lns)
        lns (remove #(str/starts-with? % "day8.re-frame-10x.") lns)
        lns (sort lns)]
    lns))

(defn secrets []
  (let [settings (subscribe [:settings])]
    @settings))

(defn secret [k]
  (k (secrets)))

(defn r-doc [s]
  (clj-eval-cb (gstring/format "(clojisr.v1.gorilla.repl/r-doc %s)" s)
               (fn [data]
                 (let [{:keys [value error]} data]
                   (if error
                     (println "r docstring error: " error)
                     (println "r docstring" value))))))

(comment

  (r-doc "cor")


  ;
  )