(ns twos-1-10-1.core
  (:require [clojure.spec.alpha        :as         s]
            [clojure.spec.gen.alpha    :as       gen]
            [clojure.data.priority-map :refer   :all])
  (:gen-class))

(comment ---- TIME WARP OPERATING SYSTEM --------------------------------)
;;  _____ _             __      __
;; |_   _(_)_ __  ___ __\ \    / /_ _ _ _ _ __
;;   | | | | '  \/ -_)___\ \/\/ / _` | '_| '_ \
;;   |_| |_|_|_|_\___|    \_/\_/\__,_|_| | .__/
;;                                       |_|
;;   ___                     _   _             ___         _
;;  / _ \ _ __  ___ _ _ __ _| |_(_)_ _  __ _  / __|_  _ __| |_ ___ _ __
;; | (_) | '_ \/ -_) '_/ _` |  _| | ' \/ _` | \__ \ || (_-<  _/ -_) '  \
;;  \___/| .__/\___|_| \__,_|\__|_|_||_\__, | |___/\_, /__/\__\___|_|_|_|
;;       |_|                           |___/       |__/

;;; See https://blog.acolyer.org/2015/08/20/virtual-time/
;;; http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.134.6637&rep=rep1&type=pdf
;;; http://dl.acm.org/citation.cfm?id=37508
;;; ftp.cs.ucla.edu/tech-report/198_-reports/870042.pdf

;;; --------- DESIGN APPROACH --------------------------------------------

;;; Many data types come with a protocol, a record type, a spec, and tests.

;;; The protocol for each type declares functions. Types that adhere to the
;;; protocol implement those functions. For instance, the MessageQueueT protocol
;;; declares that every message queue must implement "fetch-bundle,"
;;; "insert-message (with potential annihilation)," and "delete-message-by-mid."

;;; Two types implement this protocol: input queues and output queues. The
;;; signatures of these functions are identical for both types even though those
;;; two types of queues are prioritized differently (by receive-time for input
;;; queues and by send-times for output queues).

;;; A record for a type (1) provides constructors, (2) implements protocols, (3)
;;; relieves clojure.spec from specifying required fields. For instance, we do
;;; not need to spec each field of a message if we define a record that requires
;;; those fields. Even when there is only one record type implementing a given
;;; protocol, record seems the most elegant way to package the relationships
;;; amongst protocols, hashmap-like data structures, and specs. There is a
;;; discussion of this issue in the Clojure groups [at this
;;; URL](https://goo.gl/5USUP9).

;;; Specs assert logical properties of (instances of) types. For instance, the
;;; spec for an ::input-queue asserts that every input-queue must be a
;;; ::priority-map prioritized on "vals," with "val" being the second element of
;;; each key-value pair. Every "val" must be a virtual time and every
;;; virtual-time must equal the receive-time of the message that resides in the
;;; key position of each key-value pair in the priority map. The spec generates
;;; tests in which the virtual times are pulled from the receive-time fields of
;;; messages. The tests in the main test file, core_test.clj, check this
;;; property (somewhat vacuously, because the property is true by construction;
;;; the test future-proofs us against changes in the spec and its test
;;; generator). The tests check this property with a "defspec" that lives in the
;;; test file (see test #23.)

;;; Tests of assertions that are true by construction is intentional. Expressly
;;; writing down such obvious cases ones is cheap future-proofing and only bulks
;;; up the test file.

;;; -------- NAMING CONVENTIONS -----------------------------------------

;;; The names of "private-ish" functions begin with a hyphen. Such functions may
;;; still be called, say for testing, without the fully qualified
;;; namespace-and-var syntax (@#'foobar).

;;; -------- DEFRECORD --------------------------------------------------

;;; Records are in kebab-case, sometimes prepended with "tw-" to avoid ambiguity
;;; with more general ideas like "messages". Records create Java classes in
;;; partial snake_case behind the scenes. For instance, the fully qualified name
;;; of the message record type is twos_1_10_1.core.message.

;;; (defrecord tw-message   [sender send-time ...]
;;; (defrecord input-queue  [iq-priority-map]
;;; (defrecord output-queue [oq-priority-map]
;;; (defrecord tw-state     [send-time ...]
;;; (defrecord tw-process   [event-main ...]

;;; -------- DEFPROTOCOLS -----------------------------------------------

;;; Protocols are in PascalCase and suffixed with a "T," which means "type" and
;;; reminds us of the common C and C++ convention.

;;; (defprotocol MessageT
;;; (defprotocol MessageQueueT
;;; (defprotocol StateQueueT
;;; (defprotocol ProcessQueueT

;;; -------- PRIMARY SPECS -----------------------------------------------

;;; Specs for records are autoresolved keywords (double-colon), with names
;;; exactly like the records they refer to, with leading "tw-" removed.

;;; (s/def ::virtual-time
;;; (s/def ::message (s/and ::potentially-acausal-message-hashmap ...
;;; (s/def ::state   (s/keys :req-un [::send-time ::body]))
;;; (s/def ::process (s/keys :req-un [::event-main ...

;;; -------- SUBORDINATE SPECS -------------------------------------------

;;; Time Warp is a Virtual-Time Operating System. It uses abbreviated
;;; nomenclature traditional in operating systems like "mid" for "message-id,"
;;; "pid" for "process-id," and "pcb" for "process-control block."

;;; (s/def ::mid  uuid?)
;;; (s/def ::pid  uuid?)

;;; (s/def ::sender       ::pid)
;;; (s/def ::send-time    ::virtual-time)
;;; (s/def ::receiver     ::pid)
;;; (s/def ::receive-time ::virtual-time)
;;; (s/def ::body         any?)
;;; (s/def ::sign         #{-1 1})
;;; (s/def ::message-id   ::mid)

;;; (s/def ::potentially-acausal-message-hashmap

;;; (s/def ::input-message
;;; (s/def ::output-message

;;; (s/def ::message-pair
;;; (s/def ::priority-map
;;; (s/def ::input-message-and-receive-time-pair
;;; (s/def ::input-queue
;;; (s/def ::output-message-and-send-time-pair
;;; (s/def ::output-queue
;;; (s/def ::local-virtual-time ::virtual-time)

;;; (s/def ::event-main any?) ;; Actually a void-returning function TODO
;;; (s/def ::query-main any?) ;; Actually a void-returning function TODO

(comment ---- UTILITY FUNCTIONS -----------------------------------------)
;;  _   _ _   _ _ _ _          ___             _   _
;; | | | | |_(_) (_) |_ _  _  | __|  _ _ _  __| |_(_)___ _ _  ___
;; | |_| |  _| | | |  _| || | | _| || | ' \/ _|  _| / _ \ ' \(_-<
;;  \___/ \__|_|_|_|\__|\_, | |_| \_,_|_||_\__|\__|_\___/_||_/__/
;;                      |__/

(defn new-uuid
  "Generate a type-4 UUID with a strong pseudo-random number generator (PRNG)
  https://goo.gl/p0roil."
  []
  (java.util.UUID/randomUUID))

(comment ---- BASIC SPECS -----------------------------------------------)
;;  ___          _      ___
;; | _ ) __ _ __(_)__  / __|_ __  ___ __ ___
;; | _ \/ _` (_-< / _| \__ \ '_ \/ -_) _(_-<
;; |___/\__,_/__/_\__| |___/ .__/\___\__/__/
;;                         |_|

(comment -------- MESSAGE AND PROCESS IDS -------------------------------)

;;; We don't really need more discriminating types for ::mid and ::pid because
;;; they don't support an interesting protocol. The only possible benefit seems
;;; prevention of confusion between the two, as in using an ::mid where a ::pid
;;; is expected or vice versa. The risk does not seem greater than the overhead
;;; of an additional layer of type information.

(s/def ::mid  uuid?)
(s/def ::pid  uuid?)

(def -new-mid new-uuid)
(def -new-pid new-uuid)

(comment -------- VIRTUAL TIME ------------------------------------------)
;; __   ___     _             _   _____ _
;; \ \ / (_)_ _| |_ _  _ __ _| | |_   _(_)_ __  ___
;;  \ V /| | '_|  _| || / _` | |   | | | | '  \/ -_)
;;   \_/ |_|_|  \__|\_,_\__,_|_|   |_| |_|_|_|_\___|

;;; Thanks to James Reeves for inspiring this simplification
;;; (https://goo.gl/5USUP9)

;;; TODO: May require a density axiom in the future: "between any two finite
;;; virtual times there is another virtual time." Supporting that axiom may
;;; require more type infrastructure. See https://goo.gl/aQzWxv and
;;; https://goo.gl/fKz8sl.

(s/def ::virtual-time
  (s/with-gen
    (s/and number? #(not (Double/isNaN %)))
    ;; We'd like most values generated in tests to be finite, with the
    ;; occasional infinity. Adjust these frequencies to taste.
    #(gen/frequency [[98 (s/gen number?)]
                     [ 1 (gen/return Double/NEGATIVE_INFINITY)]
                     [ 1 (gen/return Double/POSITIVE_INFINITY)]])))

;;; In the REPL, try (s/exercise ::virtual-time)

(comment ---- MESSAGE ---------------------------------------------------)
;;  __  __
;; |  \/  |___ ______ __ _ __ _ ___
;; | |\/| / -_|_-<_-</ _` / _` / -_)
;; |_|  |_\___/__/__/\__,_\__, \___|
;;                        |___/

;;; In classic Time Warp, the sender (space coordinate, process id) and
;;; send-time (time coordinate, virtual time) are separated; ditto for receivers
;;; and receive-times. TODO: Experimentally, abstract spacetime points into a
;;; separate type.

;;; Messages satisfy the following protocol on their signs.

(defprotocol MessageT
  (match-ids-opposite-signs [this that])
  (match-but-id             [this that])
  (flip-sign                [this]))

;;; Support for the message protocol.

(defn -messages-match-ids-with-opposite-signs
  "Needed for annihilation."
  [m1 m2]
  (and (= (:message-id m1) (:message-id m2))
       (= (:sign m1) (- (:sign m2)))))

(defn -messages-match-but-for-id
  "True iff messages match in all attributes except id; needed for lazy
  cancellation."
  [m1 m2]
  (and (= (:sender       m1) (:sender       m2))
       (= (:send-time    m1) (:send-time    m2))
       (= (:receiver     m1) (:receiver     m2))
       (= (:receive-time m1) (:receive-time m2))
       (= (:body         m1) (:body         m2))
       (= (:sign         m1) (:sign         m2))
       (not (= (:message-id m1) (:message-id m2)))))

(defn -message-flip-sign
  "Needed for creating message pairs."
  [msg]
  (assoc msg :sign (- (:sign msg))))

(defrecord tw-message
    [sender   send-time
     receiver receive-time
     body     sign
     message-id]
  MessageT
  (match-ids-opposite-signs [m1 m2]
    (-messages-match-ids-with-opposite-signs m1 m2))
  (match-but-id [m1 m2] (-messages-match-but-for-id m1 m2))
  (flip-sign [m] (-message-flip-sign m)))

;;; The function map->tw-message is automatically created.

(s/def ::sender       ::pid)
(s/def ::send-time    ::virtual-time)
(s/def ::receiver     ::pid)
(s/def ::receive-time ::virtual-time)
(s/def ::body         any?)
(s/def ::sign         #{-1 1})
(s/def ::message-id   ::mid)

;;; Messages with receive-time in the virtual past or present are of theoretical
;;; interest only. Define messages with arbitrary send and receive times as
;;; "potentially acausal." Spec a hashmap for them to make it easier to generate
;;; instances. :req-un means "required, unqualified." Spec translates
;;; namespace-qualified keys to unqualified keys (TODO: Why did we specify
;;; unqualified keys?)

(s/def ::potentially-acausal-message-hashmap
  (s/keys :req-un [::sender     ::send-time
                   ::receiver   ::receive-time
                   ::body       ::sign
                   ::message-id ]))

;;; Time-Warp Classic only allows causal messages. We call them just "messages."
;;; Note that a message hashmap wrapped in a defrecord, while acting mostly like
;;; a hashmap, does not satisfy an s/keys spec. Therefore, we insert an extra
;;; layer that just generates and filters hashmaps. TODO: is there a better way
;;; to fulfill the protocol-record-spec pattern?

(s/def ::message
  (s/with-gen
    (s/and #(instance? twos_1_10_1.core.tw-message %)
           #(< (:send-time %) (:receive-time %)))
    #(gen/fmap map->tw-message
               (s/gen ::potentially-acausal-message-hashmap))))

;;; In the REPL, try (s/exercise ::message)

;;; Input messages are always positive.

(s/def ::input-message (s/and ::message #(= (:sign %) 1)))

;;; Output messages are always negative.

(s/def ::output-message (s/and ::message #(= (:sign %) -1)))

(comment ---- MESSAGE PAIRS ---------------------------------------------)
;;  __  __                            ___      _
;; |  \/  |___ ______ __ _ __ _ ___  | _ \__ _(_)_ _ ___
;; | |\/| / -_|_-<_-</ _` / _` / -_) |  _/ _` | | '_(_-<
;; |_|  |_\___/__/__/\__,_\__, \___| |_| \__,_|_|_| /__/
;;                        |___/

(s/def ::message-pair
  (s/with-gen
    (s/and (s/tuple ::message ::message)
           (fn [[m1 m2]] (and (= (:sign m1)  1)
                              (= (:sign m2) -1))))
    #(gen/bind
      (s/gen ::message)
      (fn [msg] (gen/return [msg (-message-flip-sign msg)])))))

(defn -make-message-pair
  "Produce a 2-vector (an ordered 2-tuple) of a positive message with a fresh
  random id and its antimessage. The application will indirectly invoke this
  function when preparing a message to send to another process."
  [&{:keys [sender   send-time
            receiver receive-time
            body]}]
  (let [m (map->tw-message {:sender       sender
                            :send-time    send-time
                            :receiver     receiver
                            :receive-time receive-time
                            :body         body
                            :sign         1
                            :message-id   (new-uuid)})]
    [m (-message-flip-sign m)]))

(comment ---- QUEUES ----------------------------------------------------)
;;   ___
;;  / _ \ _  _ ___ _  _ ___ ___
;; | (_) | || / -_) || / -_|_-<
;;  \__\_\\_,_\___|\_,_\___/__/

;;; A virtual-time queue is a Clojure priority-map https://goo.gl/Ls4lkv where
;;; the value of every key-value pair must be of type ::virtual-time.
;;; "priority-map" uses the value field of every item to order the items in the
;;; priority map. The priority-map type directly supports efficient peek and
;;; pop, as well as efficient fetch of a bundle set by virtual time. An empty
;;; priority map is a valid virtual-time queue.

(defprotocol MessageQueueT
  ;; A message queue is a collection of pairs of messages and virtual times such
  ;; that the virtual time in each pair matches either the receive-time (for
  ;; input queues) or the send-time (for output queues) of the message in the
  ;; same pair.
  (fetch-bundle   [q  vt])
  ;; Insert a message in a queue, potentially annihilating its antimessage.
  (insert-message [q   m])
  ;; Delete a message given only its mid.
  (delete-message-by-mid [q mid])
  ;; TODO more ...
  )

;;; Write a spec for priority maps just so we can write a test generator.

(s/def ::priority-map
  (s/with-gen
    #(instance? (class (priority-map)) %)
    ;; The generator is a function that returns an empty priority map.
    (constantly (gen/return (priority-map)))))

;;; --------- INPUT QUEUES ----------------------------------------------

;;  ___                _      ___
;; |_ _|_ _  _ __ _  _| |_   / _ \ _  _ ___ _  _ ___
;;  | || ' \| '_ \ || |  _| | (_) | || / -_) || / -_)
;; |___|_||_| .__/\_,_|\__|  \__\_\\_,_\___|\_,_\___|
;;          |_|

;;; TODO: An efficient implementation of the protocol for input queues is a
;;; bundles->receive-time priority-map of bundles (sets, not bags) of mids as
;;; keys and receive-times as values (recall that priority maps are ordered by
;;; values), plus a mid->message_hashmap from message ids to messages.
;;; Priority-map already supports the bundle map. Consider this REPL session:
;;;
;;; Priority map of mids (letters) to receive-times (numbers)
;;;
;;;     (def boo (priority-map "a" 1, "b" 2, "c" 2, "d" 1, "e" 1, "f" 3))
;;;
;;; Get at the vt-s to bundles:
;;;
;;;     (.priority->set-of-items boo)
;;; ~~> {1 #{"d" "e" "a"}, 2 #{"b" "c"}, 3 #{"f"}}
;;;
;;;     (class (.priority->set-of-items boo))
;;; ~~> clojure.lang.PersistentTreeMap ;; Thats SORTED! JUST WHAT WE NEED!
;;;
;;; The other map supported by "priority-map" is not needed externally, but it
;;; supports peek and pop.
;;;
;;;     (.item->priority boo)
;;; ~~> {"a" 1, "b" 2, "c" 2, "d" 1, "e" 1, "f" 3}
;;;
;;;     (class (.item->priority boo))
;;; ~~> clojure.lang.PersistentArrayMap
;;;
;;; We will have to maintain a separate data structure mapping mid (letters) to
;;; the full message.
;;;
;;; ~~> {"a" <some-message>, "b" <some-message>, ...}
;;;
;;; Deleting a message from an input queue is supported by a transactional
;;; "dissoc" on both structures.

;;; An input queue is inherently stateful, so we will coordinate transactions
;;; with refs, supporting multithreaded operation in a single processor node
;;; (see https://clojure.org/reference/refs).

;;; TODO: For an initial pass, we'll just do linear searches with "filter". They
;;; may be faster than principled data structures for small queues anyway.

(defmacro dump "For debugging in the extreme." [x]
  `(let [x# ~x]
     (do (println '~x "~~> ")
         (clojure.pprint/pprint x#)
         (println "")
         x#)))

(defrecord input-queue [iq-priority-map]
  MessageQueueT
  ;; fetch-bundle does not produces a queue, but rather a straight collection of
  ;; messages.
  (fetch-bundle          [q  vt]
    (->> (filter
          (fn [[msg vt2]] (= vt vt2))
          (:iq-priority-map q))
         (map first)))
  ;; TODO: insert-message is improperly exposed to insertion of duplicates.
  ;; TODO: annihilation
  (insert-message        [q   m]
    (-> q
        (:iq-priority-map)
        (assoc m (:receive-time m))
        (input-queue.)))
  ;;
  (delete-message-by-mid [q mid]
    (let [iq (:iq-priority-map q)
          ;;
          msg-vt-pair
          (filter
           (fn [[msg vt]] (= (:message-id msg))) iq)
          ;;
          new-messages
          (dissoc iq (first             ; result of the filter is a coll
                      (first            ; key is first of the results
                       msg-vt-pair)))]  ; nil if msg-vt-pair is nil (OK)
      (input-queue. new-messages))))    ; return a new input-queue.

(defn -input-message-and-receive-time-pair-gen
  "A generator for pairs of input messages and their receive times."
  []
  (gen/bind (s/gen ::input-message)
            (fn [msg] (gen/return
                       ;; 2-tuple of message and its receive time:
                       [msg (:receive-time msg)]))))

(s/def ::input-message-and-receive-time-pair
  (s/with-gen
    (s/and
     (s/tuple ::input-message ::virtual-time))
    -input-message-and-receive-time-pair-gen))

(defn -every-value-a-virtual-time
  "Check that every value in a sequence of key-value pairs is a valid virtual
  time."
  [xs]
  (every? #(s/valid? ::virtual-time %) (map second xs)))

(s/def ::input-queue
  (s/with-gen
    (s/and ::priority-map
           -every-value-a-virtual-time
           #(every? (fn [[msg vt]]
                      (= vt (:receive-time msg)))
                    %))
    #(s/gen (s/coll-of
             ::input-message-and-receive-time-pair
             :into (priority-map) :gen-max 50))))

;;; Try (first (gen/generate (s/gen ::input-queue))) in the REPL to get a
;;; randomly generated input queue.

;;; --------- OUTPUT QUEUES ---------------------------------------------

;;   ___       _             _      ___
;;  / _ \ _  _| |_ _ __ _  _| |_   / _ \ _  _ ___ _  _ ___
;; | (_) | || |  _| '_ \ || |  _| | (_) | || / -_) || / -_)
;;  \___/ \_,_|\__| .__/\_,_|\__|  \__\_\\_,_\___|\_,_\___|
;;                |_|

;;; TODO: It's emerging that input queue and output queue have the same
;;; implementations for "fetch-bundle." Perhaps the upper-level queue
;;; abstraction can and should hide the differences between input and output
;;; queues?

(defrecord output-queue
    [oq-priority-map]
  MessageQueueT
  ;; Note: {:vt 3} does not equal (virtual-time. 3), so we must dig out the
  ;; values from the virtual-time maps. TODO: reconsider record / protocol
  ;; implementations: too heavyweight?
  (fetch-bundle          [q  vt]
    (filter
     (fn [[msg vt2]] (= vt vt2))
     (:iq-priority-map q)))
  (insert-message        [q   m] nil)
  (delete-message-by-mid [q mid] nil))

(defn -output-message-and-send-time-pair-gen
  "A generator for pairs of output messages and their send times."
  []
  (gen/bind (s/gen ::output-message)
            (fn [msg] (gen/return
                       ;; 2-tuple of message and its receive time:
                       [msg (:send-time msg)]))))

(s/def ::output-message-and-send-time-pair
  (s/with-gen
    (s/and
     (s/tuple ::output-message ::virtual-time))
    -output-message-and-send-time-pair-gen))

(s/def ::output-queue
  (s/with-gen
    (s/and ::priority-map
           -every-value-a-virtual-time
           #(every? (fn [[msg vt]]
                      (= vt (:send-time msg)))
                    %))
    #(s/gen (s/coll-of
             ::output-message-and-send-time-pair
             :into (priority-map) :gen-max 50))))

;;  ___ _        _          ___
;; / __| |_ __ _| |_ ___   / _ \ _  _ ___ _  _ ___
;; \__ \  _/ _` |  _/ -_) | (_) | || / -_) || / -_)
;; |___/\__\__,_|\__\___|  \__\_\\_,_\___|\_,_\___|

(defprotocol StateQueueT)

;;  ___                          ___
;; | _ \_ _ ___  __ ___ ______  / _ \ _  _ ___ _  _ ___
;; |  _/ '_/ _ \/ _/ -_|_-<_-< | (_) | || / -_) || / -_)
;; |_| |_| \___/\__\___/__/__/  \__\_\\_,_\___|\_,_\___|

(defprotocol ProcessQueueT)

(comment ---- STATE -----------------------------------------------------)
;;  ___ _        _
;; / __| |_ __ _| |_ ___
;; \__ \  _/ _` |  _/ -_)
;; |___/\__\__,_|\__\___|

;;; There is some theoretical elegance to making a state exactly the same data
;;; structure as a message, interpreted as a message from a process to itself to
;;; be received at an indeterminate time. However, for now, states and messages
;;; are distinct concepts.

(s/def ::state (s/keys :req-un [::send-time ::body]))
(defrecord tw-state [send-time body])

;;; No need for a private -make-state function because there are only two
;;; arguments to a tw-state constructor.

(comment ---- PROCESS ---------------------------------------------------)
;;  ___
;; | _ \_ _ ___  __ ___ ______
;; |  _/ '_/ _ \/ _/ -_|_-<_-<
;; |_| |_| \___/\__\___/__/__/

(s/def ::local-virtual-time ::virtual-time)
(s/def ::event-main         any?) ;; Actually a void-returning function
(s/def ::query-main         any?) ;; Actually a void-returning function TODO

;; Write s/fdefs for these events ... but why? We're not generating test
;; functions, or are we?

(s/def ::process (s/keys :req-un [::event-main ::query-main
                                  ::local-virtual-time
                                  ::state-queue ::input-queue ::output-queue
                                  ::pid]))

;; TODO: protocol?

(defrecord tw-process [event-main query-main
                       local-virtual-time
                       state-queue input-queue output-queue
                       process-id])

(defn -make-process [&{:keys [event-main  query-main]}]
  (tw-process. event-main query-main
               :vt-negative-infinity
               (priority-map) (priority-map) (priority-map)
               (new-uuid)))

(defn tw-send
  [this-process receiver receive-time body]
  (let [send-time (:local-virtual-time this-process)]))

(comment ---- TODO ------------------------------------------------------)
;;  _____ ___  ___   ___
;; |_   _/ _ \|   \ / _ \
;;   | || (_) | |) | (_) |
;;   |_| \___/|___/ \___/

;;; TODO: "send" API is a protocol on "process?"

;;; TODO: Make this entire file literal with marginalia
;;; https://github.com/gdeer81/marginalia

;;; TODO:

(comment ---- MAIN ------------------------------------------------------)
(defn -main
  "I don't do a whole lot ... yet."
  [& args])
