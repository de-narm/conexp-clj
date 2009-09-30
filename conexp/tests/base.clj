(ns conexp.tests.base
  (:use clojure.test
	conexp.base))

(deftest test-cross-product
  (is (= #{[1 1] [1 2] [2 1] [2 2]} (cross-product #{1 2} #{1 2})))
  (is (and (= #{} (cross-product #{} #{1 2}))
	   (= #{} (cross-product #{1 2} #{}))
	   (= #{} (cross-product #{} #{}))))
  (is (= 12 (count (cross-product #{1 2 3} #{1 2 3 4}))))
  (is (= (* 2 3 4) (count (cross-product #{1 2} #{3 4 5} #{6 7 8 9}))))
  (is (= #{[]} (cross-product))))

(deftest test-disjoint-union
  (is (= 11 (count (disjoint-union #{1 2 3 4 5 6} #{7 8 9 10 11}))))
  (is (= (+ 1 2 3 4) (count (disjoint-union #{1} #{1 2} #{1 2 3} #{1 2 3 4}))))
  (are [set-1 set-2 set-3] (let [du (disjoint-union set-1 set-2 set-3)]
			     (and (= set-1 (set-of x [[x z] du :when (= z 0)]))
				  (= set-2 (set-of x [[x z] du :when (= z 1)]))
				  (= set-3 (set-of x [[x z] du :when (= z 2)]))))
       #{1 2 3} #{3 4 5} #{7 8 9}
       #{} #{'a *} #{}))

(deftest test-set-of-range
  (is (= 100 (count (set-of-range 0 100))))
  (is (= (set (range 0 10 2)) (set-of-range 0 10 2)))
  (is (= #{} (set-of-range 23 11))))

(deftest test-prime?
  (let [yes identity
	no  not]
    (are [x pred] (pred (prime? x))
	 2 yes
	 3 yes
	 4 no
	 7 yes
	 2311 yes)))

(deftest test-crossfoot
  'to-be-done)

(deftest test-factorial
  'to-be-done)

(deftest test-subelts
  (is (= (seq #{1 2 3 4}) (subelts #{1 2 3 4 5 6 7 8 9 10} 5)))
  (is (empty? (subelts #{1 2 3 4} 1)))
  (is (= (seq #{1 2 3 4}) (subelts #{1 2 3 4} 5)))
  (is (= (seq [5 4 2 7 8]) (subelts [5 4 2 7 8 9 2 3] 9))))

(deftest test-lectic-<_i
  (is (lectic-<_i [5 7 3 2 1] 2 #{5 3 1} #{5 3 2 1}))
  (is (lectic-<_i [5 7 3 2 1] 5 #{3} #{5 7}))
  (is (lectic-<_i [5 7 3 2 1] 3 #{2} #{3}))
  (is (lectic-<_i [5 7 3 2 1] 1 #{} #{1}))
  (is (not (lectic-<_i [5 7 3 2 1] 5 #{5 3 2} #{5 7 3 2})))
  (is (not (lectic-<_i [5 7 3 2 1] 2 #{5 7 3} #{5 7 2})))
  (is (lectic-<_i [1 nil] nil #{1} #{1 nil})))

(deftest test-lectic-<
  (is (lectic-< [5 7 3 2 1] #{} #{5}))
  (is (lectic-< [5 7 3 2 1] #{7 2 1} #{5}))
  (is (not (lectic-< [5 7 3 2 1] #{5 7 3 2 1} #{7 3 2 1}))))

(deftest test-oplus
  (let [clop #(conj % 1)]
    (are [set index result] (= (oplus [5 7 3 2 1] clop set index)
			       result)
	 #{1 2 3} 3 #{1 3}
	 #{}      1 #{1}
	 #{7 3}   3 #{7 3 1}))
  (let [clop identity]
    (are [set index result] (= (oplus [5 7 3 2 1] clop set index)
			       result)
	 #{1 2 3} 3 #{3}
	 #{}      5 #{5})))

(deftest test-next-closed-set
  (are [set next] (= (next-closed-set [3 2 1] identity set) next)
       #{}      #{1}
       #{1}     #{2}
       #{2}     #{2 1}
       #{2 1}   #{3}
       #{3}     #{3 1}
       #{3 1}   #{3 2}
       #{3 2}   #{3 2 1}
       #{3 2 1} nil)
  (are [set next] (= (next-closed-set [1 nil] identity set) next)
       #{}      #{nil}
       #{nil}   #{1}
       #{1}     #{1 nil}
       #{1 nil} nil))

(deftest test-all-closed-sets
  (is (= (all-closed-sets [5 7 3 2 1] #(union % #{3 2 1}))
	 (seq [#{3 2 1} #{7 3 2 1} #{5 3 2 1} #{5 7 3 2 1}])))
  (is (= (all-closed-sets [3 2 1] identity)
	 (seq [#{} #{1} #{2} #{2 1} #{3} #{3 1} #{3 2} #{3 2 1}])))
  (is (= (all-closed-sets [1 nil] identity)
	 (seq [#{} #{nil} #{1} #{1 nil}]))))

(deftest test-subsets
  (is (= #{#{} #{1} #{2} #{1 2}}
	 (set (subsets #{1 2}))))
  (are [x y] (= y (count (subsets (set (range x)))))
        0    1
        1    2
        2    4
        8  256
       10 1024)
  (are [my-set] (and (forall [s (subsets my-set)] (subset? s my-set))
		     (exists [s (subsets my-set)] (or (not (proper-subset? s my-set))
						      (empty? s))))
       #{}
       #{1 2}
       #{'a 4}
       #{+ 3}
       #{2 3 4 'r -}))

(deftest test-transitive-closure
  (are [x y] (= (transitive-closure x) y)
       #{[1 2] [2 1]}   #{[1 1] [2 2] [1 2] [2 1]}
       #{}              #{}
       #{[1 'a]}        #{[1 'a]}
       #{[+ -] [- *]}   #{[+ -] [- *] [+ *]}
       #{[1 2] [2 3] [3 4]} #{[1 2] [2 3] [3 4] [1 3] [1 4] [2 4]}))

(deftest test-graph-of-function?
  (are [rel src trg] (graph-of-function? rel src trg)
       #{[1 2] [2 1]} #{1 2} #{1 2}
       #{} #{} #{}
       #{} #{} #{1 2 3}
       #{[1 2] [2 3] [3 1]} #{1 2 3} #{1 2 3 4 5}
       #{[1 1] [2 1] [3 1]} #{1 2 3} #{1 7})
  (are [rel src trg] (not (graph-of-function? rel src trg))
       #{[1 1] [1 2]} #{1 2} #{1 2}
       #{} #{1} #{}
       #{[1 2] [2 3] [3 4]} #{1 2 3} #{1 2 3}
       #{[1 2] [2 2]} #{1} #{2}))
