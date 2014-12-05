(ns rewrite-clj.node.whitespace
  (:require [rewrite-clj.node.protocols :as node]))

;; ## Node

(defrecord WhitespaceNode [whitespace]
  node/Node
  (tag [_] :whitespace)
  (printable-only? [_] true)
  (sexpr [_] (throw (UnsupportedOperationException.)))
  (string [_] whitespace)

  Object
  (toString [this]
    (node/string this)))

(defrecord NewlineNode [newlines]
  node/Node
  (tag [_] :newline)
  (printable-only? [_] true)
  (sexpr [_] (throw (UnsupportedOperationException.)))
  (string [_] newlines)

  Object
  (toString [this]
    (node/string this)))

(node/make-printable! WhitespaceNode)
(node/make-printable! NewlineNode)

;; ## Constructors

(defn whitespace-node
  "Create whitespace node."
  [s]
  {:pre [(string? s)
         (re-matches #"(\s|,)+" s)
         (not (re-matches #".*[\n\r].*" s))]}
  (->WhitespaceNode s))

(defn newline-node
  "Create newline node."
  [s]
  {:pre [(string? s)
         (re-matches #"[\n\r]+" s)]}
  (->NewlineNode s))

(defn- newline?
  "Check whether a character represents a linebreak."
  [c]
  (contains? #{\return \newline} c))

(defn whitespace-nodes
  "Convert a string of whitespace to whitespace/newline nodes."
  [s]
  {:pre [(string? s)
         (re-matches #"(\s|,)+" s)]}
  (->> (partition-by newline? s)
       (map
         (fn [char-seq]
           (let [s (apply str char-seq)]
             (if (newline? (first char-seq))
               (newline-node s)
               (whitespace-node s)))))))

;; ## Utilities

(defn spaces
  "Create node representing the given number of spaces."
  [n]
  (whitespace-node (apply str (repeat n \space))))

(defn newlines
  "Create node representing the given number of newline characters."
  [n]
  (newline-node (apply str (repeat n \newline))))

(let [comma (whitespace-node ", ")]
  (defn comma-separated
    [nodes]
    (butlast (interleave nodes (repeat comma)))))

(let [nl (newline-node "\n")]
  (defn line-separated
    [nodes]
    (butlast (interleave nodes (repeat nl)))))

(let [space (whitespace-node " ")]
  (defn space-separated
    [nodes]
    (butlast (interleave nodes (repeat space)))))

;; ## Predicates

(defn whitespace?
  "Check whether a node represents whitespace."
  [node]
  (contains?
    #{:whitespace
      :newline}
    (node/tag node)))

(defn linebreak?
  "Check whether a ndoe represents linebreaks."
  [node]
  (= (node/tag node) :newline))
