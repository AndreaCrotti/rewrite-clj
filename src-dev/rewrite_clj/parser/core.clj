(ns rewrite-clj.parser.core
  (:require [rewrite-clj
             [node :as node]
             [reader :as reader]]
            [rewrite-clj.parser
             [keyword :refer [parse-keyword]]
             [string :refer [parse-string parse-regex]]
             [token :refer [parse-token]]
             [whitespace :refer [parse-whitespace]]]))

;; ## Base Parser

(def ^:dynamic ^:private *delimiter*
  nil)

(defn- dispatch
  [c]
  (cond (nil? c)               :eof
        (reader/whitespace? c) :whitespace
        (= c *delimiter*)      :delimiter
        :else (get {\^ :meta      \# :sharp
                    \( :list      \[ :vector    \{ :map
                    \} :unmatched \] :unmatched \) :unmatched
                    \~ :unquote   \' :quote     \` :syntax-quote
                    \; :comment   \@ :deref     \" :string
                    \: :keyword}
                   c :token)))

(defmulti ^:private parse-next*
  (comp #'dispatch reader/peek))

(defn parse-next
  [reader]
  (reader/read-with-meta reader parse-next*))

;; # Parser Helpers

(defn- parse-delim
  [reader delimiter]
  (reader/ignore reader)
  (->> #(binding [*delimiter* delimiter]
          (parse-next %))
       (reader/read-repeatedly reader)))

(defn- parse-printables
  [reader n & [ignore?]]
  (when ignore?
    (reader/ignore reader))
  (reader/read-n
    reader
    parse-next
    (complement node/printable-only?)
    n))

;; ## Parsers Functions

;; ### Base

(defmethod parse-next* :token
  [reader]
  (parse-token reader))

(defmethod parse-next* :delimiter
  [reader]
  (reader/ignore reader))

(defmethod parse-next* :unmatched
  [reader]
  (reader/throw-reader
    reader
    "Unmatched delimiter: %s"
    (reader/peek reader)))

(defmethod parse-next* :eof
  [reader]
  (when *delimiter*
    (reader/throw-reader reader "Unexpected EOF.")))

;; ### Whitespace

(defmethod parse-next* :whitespace
  [reader]
  (parse-whitespace reader))

(defmethod parse-next* :comment
  [reader]
  (reader/ignore reader)
  (node/comment-node (reader/read-include-linebreak reader)))

;; ### Special Values

(defmethod parse-next* :keyword
  [reader]
  (parse-keyword reader))

(defmethod parse-next* :string
  [reader]
  (parse-string reader))

;; ### Meta

(defmethod parse-next* :meta
  [reader]
  (reader/ignore reader)
  (node/meta-node (parse-printables reader 2)))

;; ### Reader Specialities

(defmethod parse-next* :sharp
  [reader]
  (reader/ignore reader)
  (case (reader/peek reader)
    nil (reader/throw-reader reader "Unexpected EOF.")
    \{ (node/set-node (parse-delim reader \}))
    \( (node/fn-node (parse-delim reader \)))
    \" (parse-regex reader)
    \^ (node/raw-meta-node (parse-printables reader 2 true))
    \' (node/var-node (parse-printables reader 1 true))
    \= (node/eval-node (parse-printables reader 1 true))
    \_ (node/uneval-node (parse-printables reader 1 true))
    (node/reader-macro-node (parse-printables reader 2))))

(defmethod parse-next* :deref
  [reader]
  (node/deref-node (parse-printables reader 1 true)))

;; ## Quotes

(defmethod parse-next* :quote
  [reader]
  (node/quote-node (parse-printables reader 1 true)))

(defmethod parse-next* :syntax-quote
  [reader]
  (node/syntax-quote-node (parse-printables reader 1 true)))

(defmethod parse-next* :unquote
  [reader]
  (reader/ignore reader)
  (let [c (reader/peek reader)]
    (if (= c \@)
      (node/unquote-splicing-node
        (parse-printables reader 1 true))
      (node/unquote-node
        (parse-printables reader 1)))))

;; ### Seqs

(defmethod parse-next* :list
  [reader]
  (node/list-node (parse-delim reader \))))

(defmethod parse-next* :vector
  [reader]
  (node/vector-node (parse-delim reader \])))

(defmethod parse-next* :map
  [reader]
  (node/map-node (parse-delim reader \})))
