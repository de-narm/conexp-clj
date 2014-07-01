;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.contrib.algorithms.downing-gallier
  (:use [conexp.base :only (not-yet-implemented, difference)]
        [conexp.fca.implications :only (premise conclusion)]))

;;; Implementation

(defn implication-graph [implications]
  (let [implications     (vec implications),
        where-in-premise (persistent!
                          (reduce (fn [map i]
                                    (reduce (fn [map m]
                                              (assoc! map m (conj (map m) i)))
                                            map
                                            (premise (implications i))))
                                  (transient {})
                                  (range (count implications))))
        numargs          (loop [numargs []
                                impls   implications]
                           (if (empty? impls)
                             numargs
                             (recur (conj numargs (count (premise (first impls))))
                                    (rest impls))))]
    [implications where-in-premise numargs]))

(defn close-with-downing-gallier [[implications in-premise numargs] input-set]
  (let [numargs (reduce (fn [numargs i]
                          (assoc! numargs i (dec (get numargs i))))
                        (transient numargs)
                        (mapcat in-premise input-set))]
    (loop [queue   (reduce (fn [queue i]
                             (if (zero? (get numargs i))
                               (conj queue i)
                               queue))
                           (clojure.lang.PersistentQueue/EMPTY)
                           (range (count numargs))),
           numargs numargs,
           result  input-set]
      (if (empty? queue)
        result
        (let [idx             (first queue),
              new             (difference (conclusion (implications idx)) result)
              [numargs queue] (reduce (fn [[numargs queue] i]
                                        (let [numargs (assoc! numargs i (dec (get numargs i)))]
                                          [numargs (if (pos? (get numargs i))
                                                     queue
                                                     (conj queue i))]))
                                      [numargs (pop queue)]
                                      (mapcat in-premise new))]
          (recur queue numargs (into result new)))))))

;;; Interface

(defn clop-by-implications [implications]
  (let [predata (implication-graph implications)]
    (fn [input-set]
      (close-with-downing-gallier predata input-set))))

(defn close-under-implications [implications input-set]
  ((clop-by-implications implications) input-set))

;;; Tests

(require '[clojure.test :as test]
         '[conexp.main :as conexp])

(test/deftest test-close-under-implications
  (test/are [start impls result]
    (= result
       (close-under-implications (map (partial apply conexp/make-implication)
                                      impls)
                                 start))
    ;;
    #{} [[#{} #{1}]] #{1},
    #{1} [[#{1} #{2}] [#{2} #{3}] [#{3} #{4}] [#{4} #{5}]] #{1 2 3 4 5},
    #{1 2 3} [[#{1 2} #{4}] [#{1 4} #{5}] [#{1 6} #{7}]] #{1 2 3 4 5}
    #{1 2 3 4 5 6 7 8} [[#{1} #{4}] [#{2} #{3}] [#{4} #{5 6 7}] [#{6 7} #{8}] [#{9 10 11} #{8}]] #{1 2 3 4 5 6 7 8}))

(test/deftest test-clop-by-implications
  (let [clop (clop-by-implications #{(conexp/make-implication #{1} #{2}),
                                     (conexp/make-implication #{3} #{4}),
                                     (conexp/make-implication #{} #{1})})]
    ;;
    (test/is (= #{1 2} (clop #{1}) (clop #{2}) (clop #{})))
    (test/is (= #{1 2 3 4} (clop #{3})))
    (test/is (= #{1 2 4} (clop #{4})))
    (test/is (= #{#{1 2} #{1 2 4} #{1 2 3 4}} (set (conexp/all-closed-sets [1 2 3 4] clop))))
    (test/is (not= #{1 2 3 4} (clop #{1 2 4})))))

(def testing-implications-1
  (map (fn [set]
         (conexp/make-implication set (conexp/adprime (conexp/diag-context 14) set)))
       (conexp/subsets (conexp/set-of-range 14))))

(def testing-implications-2
  (conexp/set-of (conexp/make-implication #{i} #{(inc i)}) | i (range 10000)))

;;;

nil
