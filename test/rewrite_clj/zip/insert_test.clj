(ns rewrite-clj.zip.insert-test
  (:require [midje.sweet :refer :all]
            [rewrite-clj.zip
             [base :as base]
             [move :as m]
             [insert :refer :all]]))

(tabular
  (fact "about whitespace-aware insertion."
        (let [elements (->> (base/of-string
                              (format ?fmt "1 2 3 4"))
                            (iterate m/next))
              loc (nth elements ?n)
              loc' (?f loc 'x)]
          (base/tag loc) => (base/tag loc')
          (base/root-string loc') => ?s))
  ?fmt      ?n   ?f               ?s
  "[%s]"    0    insert-right     "[1 2 3 4] x"
  "[%s]"    1    insert-right     "[1 x 2 3 4]"
  "[%s]"    2    insert-right     "[1 2 x 3 4]"
  "[%s]"    3    insert-right     "[1 2 3 x 4]"
  "[%s]"    4    insert-right     "[1 2 3 4 x]"
  "[%s]"    0    insert-left      "x [1 2 3 4]"
  "[%s]"    1    insert-left      "[x 1 2 3 4]"
  "[%s]"    2    insert-left      "[1 x 2 3 4]"
  "[%s]"    3    insert-left      "[1 2 x 3 4]"
  "[%s]"    4    insert-left      "[1 2 3 x 4]"
  "[%s]"    0    insert-child     "[x 1 2 3 4]"
  "[%s]"    0    append-child     "[1 2 3 4 x]"
  "[ %s]"   0    insert-child     "[x 1 2 3 4]"
  "[%s ]"   0    append-child     "[1 2 3 4 x]")
