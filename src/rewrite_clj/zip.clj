(ns ^{ :doc "Zipper Utilities for EDN Trees." 
       :author "Yannick Scherer" } 
  rewrite-clj.zip
  (:refer-clojure :exclude [replace next remove find 
                            map get assoc
                            seq? vector? list? map? set?
                            print])
  (:require [fast-zip.core :as z]
            [rewrite-clj.convert :as conv]
            [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn]))

;; ## Access

(defn tag
  "Get tag of structure at current zipper location."
  [zloc]
  (when zloc
    (first (z/node zloc))))

(defn value
  "Get the first child of the current zipper node."
  [zloc]
  (when zloc
    (second (z/node zloc))))

(defn sexpr
  "Get the s-expression of the current node."
  [zloc]
  (when zloc
    (conv/->sexpr (z/node zloc))))

(defn whitespace?
  "Check if the node at the current zipper location is whitespae or comment."
  [zloc]
  (contains? #{:comment :whitespace :newline} (tag zloc)))

;; ## Zipper

(defn- z-branch?
  [node]
  (when (clojure.core/vector? node)
    (let [[k & _] node]
      (contains? #{:forms :list :vector :set :map :meta :meta* :reader-macro} k))))

(defn- z-make-node
  [node ch]
  (apply vector (first node) ch))

(def edn
  "Create zipper over rewrite-clj's EDN tree structure."
  (partial z/zipper z-branch? rest z-make-node))

;; ## Convenience Functions

(defn of-string
  "Create zipper from String."
  [s]
  (when-let [tree (p/parse-string-all s)]
    (edn tree)))

(defn of-file
  "Create zipper from File."
  [f]
  (when-let [tree (p/parse-file-all f)]
    (edn tree)))

(defn print
  "Print current zipper location."
  [zloc]
  (-> zloc z/node prn/print-edn))

(defn print-root
  "Zip up and print root node."
  [zloc]
  (-> zloc z/root prn/print-edn))

(defn ->string
  "Create string from current zipper location."
  [zloc]
  (-> zloc z/node prn/->string))

(defn ->root-string
  "Zip up and create string from root node."
  [zloc]
  (-> zloc z/root prn/->string))

;; ## Skip

(defn skip
  "Skip locations that match the given predicate by applying the given movement function
   to the initial zipper location."
  [f p? zloc]
  (->> zloc
    (iterate f)
    (take-while identity)
    (take-while (complement z/end?))
    (drop-while p?)
    (first)))

(defn skip-whitespace
  "Apply movement function (default: `clojure.z/right`) until a non-whitespace/non-comment
   element is reached."
  ([zloc] (skip-whitespace z/right zloc))
  ([f zloc] (skip f whitespace? zloc)))

(defn skip-whitespace-left
  "Move left until a non-whitespace/non-comment element is reached."
  [zloc]
  (skip-whitespace z/left zloc))

;; ## Whitespace-sensitive Zipper Wrappers

(def ^:private ^:const SPACE [:whitespace " "])

;; ### Move

(def right 
  "Move right to next non-whitespace/non-comment location."
  (comp skip-whitespace z/right))

(def left 
  "Move left to next non-whitespace/non-comment location."
  (comp skip-whitespace-left z/left))

(def down
  "Move down to next non-whitespace/non-comment location."
  (comp skip-whitespace z/down))

(def up 
  "Move up to next non-whitespace/non-comment location."
  (comp skip-whitespace-left z/up))

(def next
  "Move to the next non-whitespace/non-comment location in a depth-first manner."
  (comp skip-whitespace z/next))

(def prev 
  "Move to the next non-whitespace/non-comment location in a depth-first manner."
  (comp skip-whitespace-left z/prev))

(def leftmost 
  "Move to the leftmost non-whitespace/non-comment location."
  (comp skip-whitespace z/leftmost))

(def rightmost 
  "Move to the rightmost non-whitespace/non-comment location."
  (comp skip-whitespace-left z/rightmost))

;; ### Insert

(defn insert-right
  "Insert item to the right of the current location. Will insert a space if necessary."
  [zloc item]
  (let [r (-> zloc z/right)
        item (conv/->tree item)]
    (cond (not (z/node zloc)) (-> zloc (z/replace item))
          (or (not r) (whitespace? r)) (-> zloc (z/insert-right item) (z/insert-right SPACE))
          :else (-> zloc (z/insert-right SPACE) (z/insert-right item) (z/insert-right SPACE)))))

(defn insert-left
  "Insert item to the left of the current location. Will insert a space if necessary."
  [zloc item]
  (let [r (-> zloc z/left)
        item (conv/->tree item)]
    (cond (not (z/node zloc)) (-> zloc (z/replace item))
          (or (not r) (whitespace? r)) (-> zloc (z/insert-left item) (z/insert-left SPACE))
          :else (-> zloc (z/insert-left SPACE) (z/insert-left item) (z/insert-left SPACE)))))

(defn insert-child
  "Insert item as child of the current location. Will insert a space if necessary."
  [zloc item]
  (let [r (-> zloc z/down)
        item (conv/->tree item)]
    (if (or (not r) (not (z/node r)) (whitespace? r))
      (-> zloc (z/insert-child item))
      (-> zloc (z/insert-child SPACE) (z/insert-child item)))))

(defn append-child
  "Append item as child of the current location. Will insert a space if necessary."
  [zloc item]
  (let [r (-> zloc z/down z/rightmost)
        item (conv/->tree item)]
    (if (or (not r) (not (z/node r)) (whitespace? r))
      (-> zloc (z/append-child item))
      (-> zloc (z/append-child SPACE) (z/append-child item)))))

(defn prepend-space
  "Prepend a whitespace node of the given width."
  ([zloc] (prepend-space zloc 1))
  ([zloc n] (z/insert-left zloc [:whitespace (apply str (repeat n \space))])))

(defn append-space
  "Append a whitespace node of the given width."
  ([zloc] (append-space zloc 1))
  ([zloc n] (z/insert-right zloc [:whitespace (apply str (repeat n \space))])))

(defn prepend-newline
  "Prepend a whitespace node with the given number of linebreaks."
  ([zloc] (prepend-newline zloc 1))
  ([zloc n] (z/insert-left zloc [:whitespace (apply str (repeat n \newline))])))

(defn append-newline
  "Prepend a whitespace node with the given number of linebreaks."
  ([zloc] (append-newline zloc 1))
  ([zloc n] (z/insert-right zloc [:whitespace (apply str (repeat n \newline))])))

;; ## Modify

(defn replace
  "Replace value at the given zipper location with the given value."
  [zloc v]
  (z/replace zloc (conv/->tree v)))

(defn edit
  "Replace value at the given zipper location with value of (f node-sexpr args)."
  [zloc f & args]
  (let [form (sexpr zloc)]
    (z/replace zloc (conv/->tree (apply f form args)))))

(defn remove
  "Remove value at the given zipper location. Will remove excess whitespace, too."
  [zloc]
  ;; TODO
  (z/remove zloc))

(defn splice
  "Add the current node's children to the parent branch (in place of the current node)."
  [zloc]
  (let [ch (z/children zloc)]
    (-> (reduce z/insert-right zloc (reverse ch)) z/remove z/right)))

;; ## Base Operations

(def node z/node)
(def root z/root)

(def right* z/right)
(def left* z/left)
(def up* z/up)
(def down* z/down)
(def next* z/next)
(def prev* z/prev)
(def rightmost* z/rightmost)
(def leftmost* z/leftmost)
(def replace* z/replace)
(def edit* z/edit)

;; ## Find

(defn find
  "Find element satisfying the given predicate by applying the given movement function
   to the initial zipper location."
  ([zloc p?] (find zloc right p?))
  ([zloc f p?] (->> zloc
                 (iterate f)
                 (take-while identity)
                 (take-while (complement z/end?))
                 (drop-while (complement p?))
                 (first))))

(defn find-next
  "Find element other than the current zipper location matching the given predicate by 
   applying the given movement function to the initial zipper location."
  ([zloc p?] (find-next zloc right p?))
  ([zloc f p?]
   (when-let [zloc (f zloc)]
     (find zloc f p?))))

(defn find-tag
  "Find element with the given tag by applying the given movement function to the initial 
   zipper location."
  ([zloc t] (find-tag zloc right t))
  ([zloc f t] (find zloc f #(= (tag %) t))))

(defn find-next-tag
  "Find element other than the current zipper location with the given tag by applying the 
   given movement function to the initial zipper location."
  ([zloc t] (find-next-tag zloc right t))
  ([zloc f t] (find-next zloc f #(= (tag %) t))))

(defn find-token
  "Find token element matching the given predicate by applying the given movement function
   to the initial zipper location, defaulting to `right`."
  ([zloc p?] (find-token zloc right p?))
  ([zloc f p?] (find zloc f (fn [x] (and (= (tag x) :token) (p? (value x)))))))

(defn find-next-token
  "Find token element matching the given predicate by applying the given movement function
   to the initial zipper location, defaulting to `right`."
  ([zloc p?] (find-next-token zloc right p?))
  ([zloc f p?] (find-next zloc f (fn [x] (and (= (tag x) :token) (p? (value x)))))))

(defn find-value
  "Find token element whose value matches the given one by applying the given movement
   function to the initial zipper location, defaulting ro `right`."
  ([zloc v] (find-value zloc right v))
  ([zloc f v] (find-token zloc f #(= % v))))

(defn find-next-value
  "Find token element whose value matches the given one by applying the given movement
   function to the initial zipper location, defaulting ro `right`."
  ([zloc v] (find-next-value zloc right v))
  ([zloc f v] (find-next-token zloc f #(= % v))))

;; ## Seq Operations

(defn seq?
  [zloc]
  (contains? #{:forms :list :vector :set :map} (tag zloc)))

(defn list?
  [zloc]
  (= (tag zloc) :list))

(defn vector?
  [zloc]
  (= (tag zloc) :vector))

(defn set?
  [zloc]
  (= (tag zloc) :set))

(defn map?
  [zloc]
  (= (tag zloc) :map))

(defn map
  "Apply function to all zipper locations in the seq at the current zipper location.
   If this is called on a map, the map values be traversed."
 [f zloc]
  (when-not (seq? zloc)
    (throw (Exception. (str "cannot iterate over node of type: " (tag zloc)))))
  (if (map? zloc)
    (loop [loc (down zloc)
           parent zloc]
      (if-not (and loc (z/node loc))
        parent
        (if-let [v0 (right loc)]
          (if-let [v (f v0)]
            (recur (right v) (up v))
            (recur (right v0) parent))
          parent)))
    (loop [loc (down zloc)
           parent zloc]
      (if-not (and loc (z/node loc))
        parent
        (if-let [v (f loc)]
          (recur (right v) (up v))
          (recur (right loc) parent))))))

(defn map-keys
  "Apply function to all keys of the map at the current zipper location"
  [f zloc]
  (when-not (map? zloc)
    (throw (Exception. (str "cannot iterate over keys of node of type: " (tag zloc)))))
  (loop [loc (down zloc)
         parent zloc]
    (if-not (and loc (z/node loc))
      parent
      (if-let [v (f loc)]
        (recur (right (right v)) (up v))
        (recur (right (right loc)) parent)))))

(defn get
  "If a map is given, get element with the given key; if a seq is given, get nth element."
  [zloc k]
  (cond (map? zloc) (when-let [v (-> zloc down (find-value k))]
                      (right v))
        (seq? zloc) (let [elements (->> zloc down (iterate right) (take-while identity))]
                      (nth elements k))
        :else (throw (Exception. (str "cannot get element of node of type: " (tag zloc))))))

(defn assoc
  "Set map/seq element to the given value."
  [zloc k v]
  (if-let [vloc (get zloc k)]
    (-> vloc (replace v) up)
    (if (map? zloc)
      (-> zloc (append-child k) (append-child v))
      (throw (Exception. (str "not a valid index to assoc: " v))))))

;; ## Edit Scope

(defn subzip
  "Create zipper that only contains the given zipper location's node."
  [zloc]
  (when zloc
    (edn (z/node zloc))))

(defmacro edit->
  "Will pass arguments to `->`. Return value will be the state of the input node
   after all modifications have been performed. This means that the result is 
   automatically 'zipped up' to represent the same location the macro was given."
  [zloc & body]
  `(let [zloc# ~zloc
         loc# (subzip zloc#)]
     (if-let [edit# (-> loc# ~@body)]
       (z/replace zloc# (z/root edit#))
       (throw (Exception. "Body of edit-> did not return value.")))))

;; ## Walk

(defn prewalk
  "Perform a depth-first pre-order traversal starting at the given zipper location
   and apply the given function to each child node. If a predicate `p?` is given, 
   only apply the function to nodes matching it."
  ([zloc f] (prewalk zloc (constantly true) f))
  ([zloc p? f]
   (loop [loc (subzip zloc)
          prv loc]
     (if-let [n0 (find loc next p?)]
       (if-let [n1 (f n0)]
         (recur (next n1) n1)
         (recur (next n0) n0))
       (z/replace zloc (z/root prv))))))
