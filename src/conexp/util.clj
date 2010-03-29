;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.util
  (:use clojure.contrib.profile
    [clojure.contrib.math :only (round)]
    [clojure.contrib.string :only (join)]
    clojure.test)
  (:import javax.swing.JOptionPane
	   java.util.Calendar
	   java.text.SimpleDateFormat))


;;; Namespace documentation

(defmacro update-ns-meta!
  "Updates meta hash of given namespace with given description."
  [ns & key-value-description]
  `(alter-meta! (find-ns '~ns)
		(fn [meta-hash#]
		  (merge meta-hash# ~(apply hash-map key-value-description)))))

(update-ns-meta! conexp.util
  :doc "Loose collection of some useful functions and macros for conexp.")


;;; Testing

(defmacro tests-to-run
  "Defines tests to run when the namespace in which this macro is
  called is tested by test-ns."
  [& namespaces]
  `(defn ~'test-ns-hook []
     (dosync
      (ref-set *report-counters*
	       (merge-with + ~@(map (fn [ns]
				      `(do
					 (require '~ns)
					 (test-ns '~ns)))
				    namespaces))))))


;;; Types

;; work over this and make it more flexible
;; we may need our own hierachy?

(defn math-type
  "Dispatch function for multimethods. Identifies sets and sequences
  as :conexp.util/set and functions as :conexp.util/fn, all other as
  :conexp.util/other."
  [thing]
  (cond
    (or (map? thing)
        (fn? thing))         ::fn
    (or (set? thing)
	(sequential? thing)) ::set
    :else                    ::other))


;;; Technical Helpers

(defn ensure-length
  "Fills given string with padding to have at least the given length."
  ([string length]
     (ensure-length string length " "))

  ([string length padding]
     (apply str string (repeat (- length (count string)) padding))))

(defn flatten
  "Flattens in to be a sequence of depth 1 at most."
  [in]
  (cond
    (sequential? in) (apply concat (map flatten in))
    :else (seq [in])))

(defn with-str-out
  "Returns string of all output being made in (flatten body)."
  [& body]
  (with-out-str 
    (doseq [element (flatten body)]
      (print element))))

(defn zip
  "Returns sequence of pairs [x,y] where x runs through seq-1 and
  y runs through seq-2 simultaneously. This is the same as
  (map #(vector %1 %2) seq-1 seq-2)."
  [seq-1 seq-2]
  (map #(vector %1 %2) seq-1 seq-2))

(defn first-non-nil
  "Returns first non-nil element in seq."
  [seq]
  (first (drop-while #(= nil %) seq)))

(defn split-at-first
  "Splits given sequence at first element satisfing predicate.
  The first element satisfing predicate will be in the second sequence."
  [predicate sequence]
  (let [index (or (first-non-nil
		   (map #(if (predicate %1) %2)
			sequence (iterate inc 0)))
		  (count sequence))]
    (split-at index sequence)))

(defn split-at-last
  "Splits given sequence at last element satisfing predicate.
  The last element satisfing predicate will be in the first sequence."
  [predicate sequence]
  (let [index (or (first-non-nil
		   (map #(if (predicate %1) %2)
			(reverse sequence) (range (count sequence) 0 -1)))
		  0)]
    (split-at index sequence)))

(defn- die-with-error
  "Stops program by raising the given error with strings as message."
  [#^Throwable error strings]
  (throw (.. error
	     (getConstructor (into-array Class [String]))
	     (newInstance (into-array Object [(apply str strings)])))))

(defn illegal-argument
  "Throws IllegalArgumentException with given strings as message."
  [& strings]
  (die-with-error IllegalArgumentException strings))

(defn unsupported-operation
  "Throws UnsupportedOperationException with given strings as message."
  [& strings]
  (die-with-error UnsupportedOperationException strings))

(defn illegal-state
  "Throws IllegalStateException with given strings as message."
  [& strings]
  (die-with-error IllegalStateException strings))

(defmacro with-profiled-fns
  "Runs code in body with all given functions being profiled."
  [fns & body]
  `(binding ~(vec (apply concat
			 (for [fn (distinct fns)]
			   `[~fn (let [orig-fn# ~fn]
				   (fn [& args#]
				     (prof ~(keyword fn)
				       (apply orig-fn# args#))))])))
     (let [data# (with-profile-data ~@body)]
       (if (not (empty? data#))
	 (print-summary (summarize data#))))))

(defmacro with-memoized-fns
  "Runs code in body with all functions in functions memoized."
  [functions & body]
  `(binding ~(vec (interleave functions
			      (map (fn [f] `(memoize ~f)) functions)))
     ~@body))

(defmacro memo-fn
  "Defines memoized, anonymous function."
  [name args & body]
  `(let [cache# (ref {})]
     (fn ~name ~args
       (if (contains? @cache# ~args)
	 (@cache# ~args)
	 (let [rslt# (do ~@body)]
	   (dosync
	    (alter cache# assoc ~args rslt#))
	   rslt#)))))

(defn inits
  "Returns a lazy sequence of the beginnings of sqn."
  [sqn]
  (let [runner (fn runner [init rest]
		 (if (not rest)
		   [init]
		   (lazy-seq
		     (cons init (runner (conj init (first rest))
					(next rest))))))]
    (runner [] sqn)))

(defn tails
  "Returns a lazy sequence of the tails of sqn."
  [sqn]
  (let [runner (fn runner [rest]
		 (if (not rest)
		   [[]]
		   (lazy-seq
		     (cons (vec rest) (runner (next rest))))))]
    (runner sqn)))

(defn now
  "Returns the current time in a human readable format."
  []
  (let [#^Calendar cal (Calendar/getInstance),
        #^SimpleDateFormat sdf (SimpleDateFormat. "HH:mm:ss yyyy-MM-dd")]
    (.format sdf (.getTime cal))))


;;; Math

(defmacro =>
  "Implements implication."
  [a b]
  `(if ~a ~b true))

(defn <=>
  "Implements equivalence."
  [a b]
  (or (and a b)
      (and (not a) (not b))))

(defmacro forall
  "Implements logical forall quantor."
  [bindings condition]
  `(every? identity
	   (for ~bindings ~condition)))

(defmacro exists
  "Implements logical exists quantor."
  [bindings condition]
  `(or (some identity
	     (for ~bindings ~condition))
       false))

(defmacro set-of
  "Macro for writing sets as mathematicians do (at least similar to it.)"
  [thing condition]
  `(set (for ~condition ~thing)))

(defn div
  "Integer division."
  [a b]
  (round (Math/floor (/ a b))))

(defn distinct-by-key
  "Returns a sequence of all elements of the given sequence with distinct key values,
  where key is a function from the elements of the given sequence. If two elements
  correspond to the same key, the one is chosen which appeared earlier in the sequence.

  This function is copied from clojure.core/distinct and adapted for using a key function."
  [sequence key]
  (let [step (fn step [xs seen]
	       (lazy-seq
		 ((fn [xs seen]
		    (when-let [s (seq xs)]
		      (let [f     (first xs)
			    key-f (key f)]
			(if (contains? seen key-f)
			  (recur (rest s) seen)
			  (cons f (step (rest s) (conj seen key-f)))))))
		  xs seen)))]
    (step sequence #{})))

(defn hashmap-by-function
  "Returns a hash map with the values of keys as keys and their values
  under function as values."
  [function keys]
  (reduce (fn [map k]
	    (assoc map k (function k)))
	  {}
	  keys))

(defn hashmap-from-pairs
  "Returns a hash map of given key-value pairs."
  [seq-of-pairs]
  (into {} seq-of-pairs))

(defn hash-from-attributes
  "Computes a hash value from the values returned from funs applied to this."
  [this funs]
  (reduce bit-xor 0 (map #(%1 this) funs)))

(defmacro with-printed-result
  "Prints string followed by result, returning it."
  [string & body]
  `(let [result# (do
		   ~@body)]
     (println ~string result#)
     result#))


;;; Swings

(defn get-root-cause
  "Returns original message of first exception causing the given one."
  [#^Throwable exception]
  (if-let [cause (.getCause exception)]
    (get-root-cause cause)
    (.getMessage exception)))

(defmacro with-swing-error-msg
  "Runs given code and catches any thrown exception, which is then
  displayed in a message dialog."
  [frame title & body]
  `(try
    ~@body
    (catch Exception e#
      (javax.swing.JOptionPane/showMessageDialog ~frame
						 (get-root-cause e#)
						 ~title
						 javax.swing.JOptionPane/ERROR_MESSAGE))))

;;; multimethod helpers

(defn test-list-types-object
  "Tests whether the second argument is of a child type of the elements of the
  first argument, returns the first matching parent type. Returns nil if
  the second argument is of none of the types in the list.

  Parameters:
    typelist   _list of types
    object     _object to test for 'isa?' relation"
  [typelist object]
  (let [ is-child-type? (fn [x] (isa? (type object) x))
         good-types (filter is-child-type? typelist) ]
    (first good-types)))

(defn insert-before-first-pred
  "Takes a predicate, a seq and an element and returns a seq
  that will have the element inserted right before the first
  element that fulfills the predicate.

  Parameters:
         pred   _predicate function
         coll   _sequence
         item   _new item"
  [pred coll item]
  (loop [ construct identity
          coll coll]
    (if (empty? coll) (construct (seq (list item)))
      (let [ head (first coll)
             tail (rest coll) ]
        (if (pred head) (construct (cons item coll))
          (recur (fn [x] (construct (cons head x))) tail))))))
            

(defmacro inherit-multimethod
  "Creates a new multimethod that dispatches by type hierarchy
   on the first parameter and sets the dispatcher to recognize
   all inherited types, then appends the given documentation."
  [name type-name doc-str]
  (let [name-str (str name)]
    `(let [ old-meta# (meta ((ns-map *ns*) (quote ~name)))
            old-doc-strings-val# (if (nil? old-meta#) {} (:doc-strings old-meta#))
            old-doc-strings# (if (nil? old-doc-strings-val#) {} old-doc-strings-val#)
            old-types# (if (nil? old-meta#) (list) (:type-list old-meta#))]
       (defonce ~name nil)
       (let [ old-func# ~name
              doc-strings# (conj old-doc-strings# 
                             {~type-name (str ~type-name "\n=====\n" ~doc-str)})
              is-new-type# (not (some #{~type-name} old-types#))
              types#  (if is-new-type# 
                        (insert-before-first-pred (fn [x#] (isa? ~type-name x#))
                          old-types# ~type-name)
                        old-types#)
              new-doc# (join "\n+++++\n\n" 
                         (cons (str "is a multimethod dispatched"
                                 " on type of the first argument...")
                           (map doc-strings# types#)))]
         (when is-new-type#
           (defmulti ~name (fn [x# & args#] (test-list-types-object types# x#)))
           (defmethod ~name nil [& args#] 
             (illegal-argument (str ~name-str 
                                 " called, but there is no method declared for type "
                                 (when-not (empty? args#) (type (first args#))) "!"  )))
           (defmethod ~name ~type-name [& args#] 
             (illegal-argument (str ~name-str 
                                 " called, but there is a method declared but"
                                 " not defined for type "
                                 (when-not (empty? args#) (type (first args#))) "!"  )))
           (doseq [t# old-types#]
             (defmethod ~name t# [& args#] (apply old-func# args#))))
         (.setMeta (var ~name) (conj (meta (var ~name)) 
                                 {:doc new-doc#
                                 :doc-strings doc-strings#
                                 :type-list types#}))))))
      
         
;; synchronization helpers

(defmacro dosync-wait
  "Returns a dosync block that will block until the operation
   has been carried out, the last value of the body will
   be returned."
  [ & body]
  `(let [waiting# (promise)]
     (dosync (deliver waiting# (do ~@body)))
     (deref waiting#)))

;;;

nil
