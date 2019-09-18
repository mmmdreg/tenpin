(ns tenpin.core)

(def initial-state
  "We only track the minimum amount of state:
  `rolls` are the numerical value of each roll
  `bonuses` a map of frame index to a vector of row indexes from which to draw bonus points.

  e.g. rolling a 10 (strike) in the first frame will yield {:rolls [10], :bonuses {0 [1 2]}"
  {:rolls   []
   :bonuses {}})

(defn rolls->frames
  "Given a list of `rolls`, returns a map of
  `frames` - a vector of at most 10 1-2 element tuples
  `extras` - a similar vector representing all extra rolls following the actual frames of a game."
  [rolls]
  (->> (reduce (fn [acc x]
                 (let [[head & rest] acc]
                   (cond
                     ; Do not add anything if existing frame has is a 10
                     (and (= 1 (count head)) (= 10 (first head))) (conj acc [x])

                     ; Add a frame pair to the list
                     (= 1 (count head)) (conj rest (conj head x))

                     ; Start a new frame
                     :else (conj acc [x])))) (list) rolls)
       reverse
       (partition-all 10)
       (zipmap [:frames :extras])
       (map (fn [[k v]] [k (vec v)]))
       (into {})))

(defn game-complete?
  "Checks whether a game has finished. In particular, we expect 10 frames to have been played,
  along with any bonus rolls required to satisfy any strikes/spares."
  [s]
  (let [{:keys [frames]} (rolls->frames (:rolls s))]
    (and (= (count frames) 10)

         (or (= [10] (peek frames))
             (= 2 (count (peek frames))))

         (let [bonuses (mapcat val (:bonuses s))]
           (or (empty? bonuses)
               (> (count (:rolls s)) (apply max bonuses)))))))

(defn roll
  "Given state `s` and a roll scoring `n`, returns the next state.

  Throws an error if game is already complete."
  [s n]
  {:pre [(not (game-complete? s))
         (nat-int? n)
         (<= 0 n 10)]}
  (let [{:keys [frames extras]} (rolls->frames (conj (:rolls s) n))
        current-frame (dec (count frames))
        s' (update s :rolls conj n)]

    (cond
      ; Past the last frame
      (seq extras)
      s'

      ; Strike
      (= [10] (peek frames))
      (assoc-in s' [:bonuses current-frame] [(count (:rolls s')) (inc (count (:rolls s')))])

      ; Spare
      (= 10 (apply + (peek frames)))
      (assoc-in s' [:bonuses current-frame] [(count (:rolls s'))])

      :else s')))


(defn score-card
  "Given the current game state `s`, generates a score card with frame by frame scoring and a total score.
  Uses 0 for placeholders that cannot yet be resolved\n  (e.g. if the bonus from a strike has not yet been rolled)"
  [s]
  (let [{:keys [frames]} (rolls->frames (:rolls s))
        scores (map-indexed (fn [i f]
                              (+ (apply + f)

                                 (->> (get (:bonuses s) i)
                                      (map #(get (:rolls s) % 0))
                                      (apply +)))) frames)]

    (merge s
           {:frames         (map (fn [f s] {:rolls f :score s}) frames scores)
            :total-score    (apply + scores)
            :game-complete? (game-complete? s)})))
