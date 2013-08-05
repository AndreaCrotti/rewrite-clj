(ns ^{ :doc "Implementation of lynaghk/cljx's basic semantics using rewrite-clj."}
  rewrite-clj.cljx
  (:require [rewrite-clj.zip :as z]))

;; ## Semantics
;;
;; cljx allows for Clojure forms to be prefixed with reader macros of the form
;; "#+<profile>" and "#-<profile>". If the given profile is active, everything with
;; "+" will remain in the code (minus the prefix) and everything with '-' will be removed.
;;
;;     (defn my-inc
;;       [x]
;;       #+debug (println "inc: x =" x)
;;       (+ x 1))
;;
;; If the profile 'debug' is active, the following result will be created:
;;
;;     (defn my-inc
;;       [x]
;;               (println "inc: x = " x)
;;       (+ x 1))
;;
;; Whitespace has thus to be preserved.

;; ## Implementation

(defn- cljx-macro?
  "Check if the given zipper node contains a cljx reader macro ('#+...' or '#-...')."
  [loc]
  (when (= (z/tag loc) :reader-macro)
    (let [[t sym] (z/value loc)]
      (when (and (= t :token) (symbol? sym))
        (let [^String nm (name sym)]
          (or (.startsWith nm "+") (.startsWith nm "-")))))))

(defn- replace-with-spaces
  "Replace the given reader macro node with spaces. The resulting zipper
   will be on the first element following it."
  [zloc]
  (let [w (z/length zloc)]
    (-> zloc 
      (z/prepend-space w)        ;; add space
      z/remove*                  ;; remove original (without removing an extra space)
      z/next)))                  ;; go to following node

(defn- remove-reader-macro
  "Remove the macro part of the given reader macro node."
  [zloc]
  (-> zloc 
    z/down replace-with-spaces   ;; replace the '+...'/'-...' part with spaces
    z/up z/splice                ;; remove the macro wrapper
    z/prepend-space))            ;; insert a space to make up for the missing '#'

(defn- handle-reader-macro
  "Handle a reader macro node by either removing it completely or only the macro part."
  [active-profiles zloc]
  (let [profile-node (-> zloc z/down)
        value-node (-> profile-node z/right)
        ^String profile-string (-> profile-node z/sexpr name)
        profile-active? (contains? active-profiles (.substring profile-string 1))
        print? (.startsWith profile-string "+")]
    (if (or (and profile-active? (not print?)) (and (not profile-active?) print?))
      (replace-with-spaces zloc)
      (remove-reader-macro zloc))))

(defn cljx-walk
  "Replace all occurences of profile reader macros."
  [root active-profiles]
  (z/prewalk root cljx-macro? #(handle-reader-macro active-profiles %)))

;; ## Test

(defn run-cljx-test
  [profiles]
  (let [data (z/of-string ";; Test it!
(defn my-inc
  [x]
  #+debug (println \"inc: x =\" x #-nomark \"[debug]\" {:a 0
                                                    :b 1})
  (+ x 1))")]
    (println "Original Code:")
    (z/print-root data)
    (println "\n")

    (println "Processed Code:")
    (-> data (cljx-walk (or profiles #{"debug" "nomark"})) z/print-root)
    (println)))
