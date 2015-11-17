(ns rewrite-clj.zip.zip-test
  (:require [midje.sweet :refer :all]
            [rewrite-clj.node :as node]
            [rewrite-clj.test-helpers :refer :all]
            [rewrite-clj.zip
              [base :as base]
              [zip :as z]]))

(fact "zipper starts with position [1 1]"
  (z/position (z/zipper (node/comment-node "hello"))) => [1 1])

(tabular
  (fact "z/down tracks position correctly"
    (-> (z/zipper (?type [(node/token-node "hello")]))
      z/down
      z/position) => ?pos)
  ?type            ?pos
  node/forms-node  [1 1]
  node/fn-node     [1 3]
  node/quote-node  [1 2])

(tabular
  (fact "z/right tracks position correctly"
    (let [root (base/of-string "[hello world]")
          zloc (nth (iterate z/right (z/down root)) ?n)]
      (z/position zloc) => ?pos))
  ?n ?pos
  0  [1 2]
  1  [1 7]
  2  [1 8])

(fact "z/rightmost tracks position correctly"
  (let [root (base/of-string "[hello world]")]
    (-> root z/down z/rightmost z/position) => [1 8]))

(tabular
  (fact "z/left tracks position correctly"
    (let [root (base/of-string "[hello world]")
          zloc (nth (iterate z/left (z/rightmost (z/down root))) ?n)]
      (z/position zloc) => ?pos))
  ?n ?pos
  0 [1 8]
  1 [1 7]
  2 [1 2])
