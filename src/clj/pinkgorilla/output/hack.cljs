(ns pinkgorilla.output.hack)

; used by list-alike and html
(defn temp-comp-hack
  [no-kw]
  (when no-kw (into [(keyword (first no-kw))]
                    (rest no-kw))))

#_(defn value-wrap
  [value content]
  [:span.value {:data-value value} content])