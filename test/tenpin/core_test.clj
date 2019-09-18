(ns tenpin.core-test
  (:require [clojure.test :refer :all]
            [tenpin.core :refer :all]))

(deftest test-rolls->frames
  (testing "adds frames"
    (is (= {:frames [[1]]} (rolls->frames [1])))
    (is (= {:frames [[1 2]]} (rolls->frames [1 2])))
    (is (= {:frames [[1 2] [3]]} (rolls->frames [1 2 3])))
    (is (= {:frames [[1 2] [3 4]]} (rolls->frames [1 2 3 4])))
    (is (= {:frames [[0]]} (rolls->frames [0])))
    (is (= {:frames [[0 1]]} (rolls->frames [0 1])))
    (is (= {:frames [[9 1]]} (rolls->frames [9 1])))
    (is (= {:frames [[9 1] [9 1]]} (rolls->frames [9 1 9 1])))
    (is (= {:frames [[10]]} (rolls->frames [10])))
    (is (= {:frames [[10] [10]]} (rolls->frames [10 10])))
    (is (= {:frames [[10] [10] [1]]} (rolls->frames [10 10 1]))))

  (testing "assumes inputs are valid so does not check them"
    (is (= {:frames [[1 10]]} (rolls->frames [1 10])))))


(deftest test-game-complete?
  (testing "without bonus points"
    (is (false? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1]})))
    (is (false? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1]})))
    (is (true? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1]}))))

  (testing "spare at the end needs one more roll"
    (is (false? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 9 1] :bonuses {9 [20]}})))
    (is (true? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 9 1, 1] :bonuses {9 [20]}}))))

  (testing "strike at the end needs two more rolls"
    (is (false? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 10], :bonuses {9 [19 20]}})))
    (is (false? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 10, 1], :bonuses {9 [19 20]}})))
    (is (true? (game-complete? {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 10, 1 1], :bonuses {9 [19 20]}})))))

(deftest test-roll
  (testing "fails if already completed"
    (is (thrown? Error (roll {:rolls [1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1, 1 1]} 1))))

  (testing "fails if nunber is suspicious"
    (is (thrown? Error (roll initial-state -1)))
    (is (thrown? Error (roll initial-state 11)))
    (is (thrown? Error (roll initial-state 1.2)))
    (is (thrown? Error (roll initial-state "one")))
    (is (thrown? Error (roll initial-state :1))))

  (testing "returns next state"
    (is (= {:rolls [1] :bonuses {}} (roll initial-state 1)))
    (is (= {:rolls [1 2 3] :bonuses {}} (-> initial-state (roll 1) (roll 2) (roll 3)))))

  (testing "tracks single bonuse for spares"
    (is (= {:rolls [1 9] :bonuses {0 [2]}} (-> initial-state (roll 1) (roll 9))))
    (is (= {:rolls [1 9 3 4] :bonuses {0 [2]}} (-> initial-state (roll 1) (roll 9) (roll 3) (roll 4)))))

  (testing "tracks double bonuses for strikes"
    (is (= {:rolls [10] :bonuses {0 [1 2]}} (-> initial-state (roll 10))))
    (is (= {:rolls [10 10] :bonuses {0 [1 2] 1 [2 3]}} (-> initial-state (roll 10) (roll 10))))))

(deftest test-score-card
  (testing "can score partial frames and games"
    (is (= 1 (:total-score (score-card {:rolls [1]}))))
    (is (= 2 (:total-score (score-card {:rolls [1 1]})))))

  (testing "scores a minimal game of 20 zeros"
    (is (= 0 (:total-score (score-card (reduce (fn [acc n] (roll acc n)) initial-state (repeat 20 0)))))))

  (testing "partially score a game before all bonuses have been rolled"
    (let [res (score-card (reduce (fn [acc n] (roll acc n)) initial-state (repeat 10 10)))]
      (is (= 270 (:total-score res)))
      (is (false? (:game-complete? res))))

    (let [res (score-card (reduce (fn [acc n] (roll acc n)) initial-state (repeat 11 10)))]
      (is (= 290 (:total-score res)))
      (is (false? (:game-complete? res)))))

  (testing "sample game https://www.liveabout.com/bowling-scoring-420895"
    (let [res (score-card (reduce (fn [acc n] (roll acc n)) initial-state [10 7 3 7 2 9 1 10 10 10 2 3 6 4 7 3 3]))]
      (is (= [20 17 9 20 30 22 15 5 17 13] (map :score (:frames res))))
      (is (= 168 (:total-score res)))
      (is (true? (:game-complete? res))))))
