(ns rewrite-clj.node.uneval
  (:require [rewrite-clj.node.protocols :as node]))

;; ## Node

(defrecord UnevalNode [children]
  node/Node
  (tag [_] :uneval)
  (printable-only? [_] true)
  (sexpr [_]
    (throw (UnsupportedOperationException.)))
  (string [_]
    (str "#_" (node/concat-strings children)))

  node/InnerNode
  (inner? [_] true)
  (children [_] children)
  (replace-children [this children']
    (node/assert-single-sexpr children')
    (assoc this :children (first children')))

  Object
  (toString [this]
    (node/string this)))

(node/make-printable! UnevalNode)

;; ## Constructor

(defn uneval-node
  "Create node representing an EDN uneval `#_` form."
  [children]
  (node/assert-single-sexpr children)
  (->UnevalNode children))
