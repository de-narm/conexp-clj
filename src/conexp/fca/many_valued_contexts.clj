;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.fca.many-valued-contexts
  (:use conexp.base
	conexp.fca.contexts))

(ns-doc "Many-Valued-Contexts and some functions for scaling.")

;;;

(deftype Many-Valued-Context [objects attributes incidence]
  Object
  (equals [this other]
    (generic-equals [this other] Many-Valued-Context [objects attributes incidence]))
  (hashCode [this]
    (hash-combine-hash Many-Valued-Context objects attributes incidence))
  ;;
  conexp.fca.contexts/Context
  (objects [this] objects)
  (attributes [this] attributes)
  (incidence [this] incidence))

(defn print-mv-context
  "Prints the given many-valued context mv-ctx as a value-table."
  [mv-ctx]
  (let [objs         (sort (objects mv-ctx)),
	atts         (sort (attributes mv-ctx)),
	inz          (incidence mv-ctx),

	str          #(if (nil? %) "nil" (str %))

	max-obj-len  (reduce #(max %1 (count (str %2))) 0 objs)
	max-att-lens (loop [lens (into {} (for [att atts]
					    [att (count (str att))]))
			    triples (for [g objs,
					  m atts]
				      [g m (inz [g m])])]
		       (if (empty? triples)
			 lens
			 (let [[g m w] (first triples)
			       len (count (str w))]
			   (if (> len (lens m))
			     (recur (assoc lens m len) (rest triples))
			     (recur lens (rest triples))))))]
    (with-str-out
      (ensure-length "" max-obj-len " ") " |" (for [att atts]
						[(ensure-length (str att) (max-att-lens att) " ") " "])
      "\n"
      (ensure-length "" max-obj-len "-") "-+" (for [att atts]
						(ensure-length "" (inc (max-att-lens att)) "-"))
      "\n"
      (for [obj objs]
	[(ensure-length (str obj) max-obj-len)
	 " |"
	 (for [att atts]
	   [(ensure-length (str (inz [obj att]))
			   (max-att-lens att))
	    " "])
	 "\n"]))))

(defmethod print-method Many-Valued-Context [mv-ctx out]
  (.write out (print-mv-context mv-ctx)))

;;;

(defmulti make-mv-context
  "Constructs a many-valued context from a set of objects, a set of
  attributes and an incidence relation, given as set of triples [g m w]
  or as a function from two arguments g and m to values w."
  {:arglists '([objects attributes incidence])}
  (fn [& args] (vec (map clojure-type args))))

(defmethod make-mv-context [clojure-coll clojure-coll clojure-coll]
  [objs atts inz]
  (let [objs (set objs),
        atts (set atts)]
    (Many-Valued-Context. objs atts
                          (if (map? inz)
                            (do
                              (when-not (forall [g objs, m atts]
                                          (contains? inz [g m]))
                                (illegal-argument "Incidence map for many-value-context must be total."))
                              inz)
                            (loop [hash  (transient {}),
                                   items inz]
                              (if (empty? items)
                                (persistent! hash)
                                (let [[g m w] (first items)]
                                  (recur (if (and (contains? objs g)
                                                  (contains? atts m))
                                           (assoc! hash [g m] w)
                                           hash)
                                         (rest items)))))))))

(defmethod make-mv-context [clojure-coll clojure-coll clojure-fn]
  [objs atts inz-fn]
  (Many-Valued-Context. (set objs) (set atts)
                        (map-by-fn (fn [[g m]]
                                     (inz-fn g m))
                                   (cross-product objs atts))))

(defmethod make-mv-context :default [objs atts inz]
  (illegal-argument "No method defined for types "
		    (clojure-type objs) ", "
		    (clojure-type atts) ", "
		    (clojure-type vals) ", "
		    (clojure-type inz) "."))

(defn make-mv-context-from-matrix
  "Creates a many-valued context from a given matrix of
  values. objects and attributes may either be given as numbers
  representing the corresponding number of objects and attributes
  respectively, or as collections. The number of entries in values
  must match the number of objects times the number of attributes."
  [objects attributes values]
  (let [objects    (if (number? objects) (range objects) objects),
        attributes (if (number? attributes) (range attributes) attributes),
        m          (count objects),
        n          (count attributes)]
    (assert (= (* m n) (count values)))
    (let [entries (into {} (for [i (range m), j (range n)]
                             [[(nth objects i) (nth attributes j)] (nth values (+ (* n i) j))]))]
      (make-mv-context objects attributes (fn [a b] (entries [a b]))))))

(defn make-mv-context-nc
  "Just creates a many-valued context from a set of objects, a set of
  attributes and a hashmap from pairs of objects and attributes to
  values. Does no checking, use with care."
  [objects attributes incidence]
  (assert (map? incidence))
  (Many-Valued-Context. (set objects) (set attributes) incidence))

;;;

(defn scale-mv-context
  "Scales given many-valued context mv-ctx with given scales. scales
  must be a map from attributes m to contexts K, where all possible
  values of m in mv-ctx are among the objects in K."
  [mv-ctx scales]
  (assert (map? scales))
  (let [inz (incidence mv-ctx),
	objs (objects mv-ctx),
	atts (set-of [m n] [m (attributes mv-ctx)
			    n (attributes (scales m))]),
	inz (set-of [g [m n]] [g objs
			       [m n] atts
			       :let [w (inz [g m])]
			       :when ((incidence (scales m)) [w n])])]
    (make-context objs atts inz)))

(defn nominal-scale
  "Returns the nominal scale on the set base."
  [base]
  (diag-context base))

(defn ordinal-scale
  "Returns the ordinal scale on the set base, optionally given an
  order relation <=."
  ([base]
     (ordinal-scale base <=))
  ([base <=]
     (make-context base base <=)))

(defn interordinal-scale
  "Returns the interordinal scale on the set base, optionally given
  two order relations <= and >=."
  ([base]
     (interordinal-scale base <= >=))
  ([base <= >=]
     (let [objs base,
	   atts-<= (map #(str "<= " %) base),
	   atts->= (map #(str ">= " %) base),
	   inz-<= (set-of [g (str "<= " m)]
			  [g objs
			   m base
			   :when (<= g m)]),
	   inz->= (set-of [g (str ">= " m)]
			  [g objs
			   m base
			   :when (>= g m)])]
       (make-context base (union atts-<= atts->=) (union inz-<= inz->=)))))

(defn biordinal-scale
  "Returns the biordinal scale on the sequence base, optionally given
  two order relations <= and >=. Note that base must be
  ordered (e.g. vector or list), because otherwise the result will be
  arbitrary."
  ([base n]
     (biordinal-scale base n <= >=))
  ([base n <= >=]
     (let [first-objs (take n base),
	   rest-objs (drop n base),

	   first-atts (map #(str "<= " %) first-objs),
	   rest-atts (map #(str ">= " %) rest-objs),

	   first-inz (set-of [g (str "<= " m)]
			     [g first-objs
			      m first-objs
			      :when (<= g m)]),
	   rest-inz (set-of [g (str ">= " m)]
			    [g rest-objs
			     m rest-objs
			     :when (>= g m)])]
       (make-context base
		     (union first-atts rest-atts)
		     (union first-inz rest-inz)))))

(defn dichotomic-scale
  "Returns the dichotimic scale on the set base. Note that base must
  have exactly two arguments."
  [base]
  (assert (= 2 (count base)))
  (diag-context base))

;;;

(defmacro scale-context
  "Scales the given (many-valued) context ctx with the given
  scales. These are of the form

    [att_1 att_2 ...] short_scale,

  where att_i is an attribute of the given context and short_scale
  determines a call to a known scala, without the attributes. For
  example, you may use this macro with

    (scale-context ctx
                   [a b c] (nominal-scale)
                   [d]     (dichotomic-scale))

  Note that attributes always have to be given in a sequence, even if
  there is only one."
  [ctx & scales]
  (let [scales (partition 2 scales),
        given-atts (mapcat first scales)]
    (when (not= given-atts (distinct given-atts))
      (illegal-argument "Doubly given attribute."))
    `(do
       (when-not (= (attributes ~ctx) ~(set given-atts))
         (illegal-argument "Given scalas to scale-context do not "
                           "yield the attribute set of the given context."))
       (scale-mv-context ~ctx
                         ~(into {}
                                (for [[atts scale] scales,
                                      att atts]
                                  `['~att ~(list* (first scale) `(attributes ~ctx) (rest scale))]))))))

;;;

nil
