(ns rewrite-clj.zip.walk-test
  (:require [midje.sweet :refer :all]
            [rewrite-clj.zip
             [base :as base]
             [edit :as e]
             [seq :as sq]
             [walk :as w]]))

(fact "about zipper tree prewalk."
      (let [root (base/of-string  "[0 [1 2 3] 4]")
            not-vec? (complement sq/vector?)
            inc-node #(e/edit % inc)
            inc-node-p #(if (sq/vector? %) % (inc-node %))]
        (-> root
            (w/prewalk identity)
            base/root-string) => "[0 [1 2 3] 4]"
        (-> root
            (w/prewalk inc-node-p)
            base/root-string) => "[1 [2 3 4] 5]"
        (-> root
            (w/prewalk not-vec? inc-node)
            base/root-string) => "[1 [2 3 4] 5]"
        (-> (iterate #(w/prewalk % not-vec? inc-node) root)
            (nth 3)
            base/root-string) => "[3 [4 5 6] 7]"))

(fact "about zipper tree postwalk."
      (let [root (base/of-string "[0 [1 2 3] 4]")
            wrap-node #(e/edit % list 'x)
            wrap-node-p #(if (sq/vector? %) (wrap-node %) %)]
        (-> root
            (w/postwalk identity)
            base/root-string) => "[0 [1 2 3] 4]"
        (-> root
            (w/postwalk wrap-node-p)
            base/root-string) => "([0 ([1 2 3] x) 4] x)"
        (-> root
            (w/postwalk sq/vector? wrap-node)
            base/root-string) => "([0 ([1 2 3] x) 4] x)"))
