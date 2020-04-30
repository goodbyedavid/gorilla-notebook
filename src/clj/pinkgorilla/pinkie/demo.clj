(ns pinkgorilla.pinkie.demo
  (:require
   [pinkgorilla.pinkie.dsl :refer [code-add result-set]]))

(comment
 
  (code-add 1 "(- 100 5)")
  (result-set 1 95)

  (code-add 2 "[1 2 {:a 1 :b 2 :c 3}]")
  (result-set 2 [1 2 {:a 1 :b 2 :c 3}])

  (def data
    {:$schema "https://vega.github.io/schema/vega-lite/v4.json"
     :description "A simple bar chart with embedded data."
     :data {:values [{:a "A" :b 28} {:a "B" :b 55} {:a "C" :b 43} {:a "D" :b 91} {:a "E" :b 81} {:a "F" :b 53}
                     {:a "G" :b 19} {:a "H" :b 87} {:a "I" :b 52} {:a "J" :b 127}]}
     :mark "bar"
     :encoding {:x {:field "a" :type "ordinal"}
                :y {:field "b" :type "quantitative"}}})

  (code-add 3 (pr-str ^:R [:p/vega data]))
  (result-set 3 ^:R [:p/vega data])

  (code-add 4 (pr-str ^:R  [:p/player "https://www.youtube.com/watch?v=Bs44qdAX5yo"]))
  (result-set 4 ^:R [:p/player "https://www.youtube.com/watch?v=Bs44qdAX5yo"])

  ; comment end
  )