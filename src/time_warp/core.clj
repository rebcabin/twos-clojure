(ns time-warp.core
  (:require [clojure.spec              :as         s]
            [clojure.spec.gen          :as       gen]
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

;;; DESIGN APPROACH ------------------------------------------------------------------

;;; Each interesting data type comes with a protocol, a record types, and a
;;; spec.
;;;
;;; The protocol for each type declares functions that records adhering to the
;;; protocol must implement. For instance, the MessageQueueT protocol declares
;;; that every message queue must implement "fetch-bundle," "insert-message
;;; (with potential annihilation)," and "delete-message-by-mid." These functions
;;; are identical for both input queues and output queues despite the fact that
;;; those two types of queues are prioritized by different fields of the
;;; messages (receive-time for input queues and send-times for output queues).
;;; The hiding of that impertinent difference is useful because it reduces the
;;; the visibility of unnecessary detail at certain levels and the size of the
;;; code overall. Those reductions, in turn, makes it easier to refactor or
;;; otherwise modify the code as we develop it.

;;; Having a record for each type serves two purposes: (1) handy constructors
;;; for instances, (2) a supported place to "hang" implementations of protocols.
;;; For instance, there is an input-queue record and an output-queue record,
;;; each implementing the MessageQueueT protocol. Even when there is only one
;;; record type implementing a given protocol, the "record" seems the most
;;; elegant way currently available in Clojure to package the relationships
;;; amongst protocols, hashmappy data structures like records and priority-maps,
;;; and specs.

;;; Specs assert logical properties of (instances of) types. For instance, the
;;; spec for an ::input-queue asserts that every instance must be a
;;; ::priority-map prioritized on "vals," with "val" in the formal sense of the
;;; second element of each key-value pair in the priority map. Every "val" must
;;; be a virtual time and every virtual-time val must equal the receive-time of
;;; the message in the key position of each key-value pair in the priority map.
;;; The spec further provides a test generator in which the virtual times are
;;; pulled from the receive-time fields of messages, as they must be for an
;;; input queue, and the tests in the main test file, core_test.clj, check this
;;; property. They check this property with a "defspec" that lives in the test
;;; file (see test #23.)

;;; Records act like hashmaps in most (if not all) respects, so they can conform
;;; to specs written with the spec primitive "s/keys" (see test #8). This is a
;;; brilliant bit of design in test.spec that brings Clojure programming
;;; assurance to the level of statically typed languages.

;;; There is some intentional redundancy in the spec in the main file, core.clj,
;;; and the defspec in the test file, core_test.clj. This is a side-effect of
;;; the interactive and incremental style of development, where we leave old
;;; tests in, by default, until we're sure they're wrong, at the cost of
;;; occasional redundancy. This is cheap assurance.

;;; NAMING CONVENTIONS ---------------------------------------------------------------

;;; DEFRECORD ------------------------------------------------------------------------

;;; Records are in kebab-case, sometimes prepended with "tw-" to avoid ambiguity
;;; with more general ideas. Records create Java classes in partial snake_case
;;; behind the scenes. For instance, the fully qualified name of the
;;; virtual-time record type is time_warp.core.virtual-time. Try it in the REPL:
;;; keyboard in (class time_warp.core.virtual-time) and see that the type is
;;; java.lang.Class. Notice that the namespace portion of the name has been
;;; converted to snake_case. Contrast the constructor, automatically created by

;;; Clojure, which has name time-warp.core/->virtual-time, in kebab-case. Try it
;;; in the REPL: keyboard in (time-warp.core/->virtual-time 42) and notice that
;;; the result is an instance of time_warp.core.virtual-time in mixed snake_case
;;; and kebab-case. Also notice that the call (virtual-time. 42) in the
;;; time-warp.core (kebab) namespace produces exactly the same result, but the
;;; fully qualified name of that constructor is time_warp.core.virtual-time,
;;; with var #'time_warp.core.virtual-time (snake.kebab). Either of the
;;; following two utterances produces exactly the same results:
;;; (time_warp.core.virtual-time. 42), (#'time_warp.core.virtual-time. 42).

;;; TODO: Consider swapping the naming conventions for records and protocols to
;;; mitigate the distracting detail about case conversions.

;;; (defrecord virtual-time [vt]
;;; (defrecord tw-message   [sender send-time ...]
;;; (defrecord input-queue  [iq-priority-map]
;;; (defrecord output-queue [oq-priority-map]
;;; (defrecord tw-state     [send-time ...]
;;; (defrecord tw-process   [event-main ...]

;;; DEFPROTOCOLS ---------------------------------------------------------------------

;;; Protocols are in PascalCase and suffixed with a "T," which means "type."

;;; (defprotocol VirtualTimeT
;;; (defprotocol MessageT
;;; (defprotocol MessageQueueT
;;; (defprotocol StateQueueT
;;; (defprotocol ProcessQueueT

;;; PRIMARY SPECS --------------------------------------------------------------------

;;; Specs for records are autoresolved keywords (double-colon), with names
;;; exactly like the records they refer to, with leading "tw-" removed.

;;; (s/def ::virtual-time
;;; (s/def ::message (s/and ::potentially-acausal-message-hashmap ...
;;; (s/def ::state   (s/keys :req-un [::send-time ::body]))
;;; (s/def ::process (s/keys :req-un [::event-main ...

;;; SUBORDINATE SPECS ----------------------------------------------------------------

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

;;; EXPERIMENTAL SPECS ---------------------------------------------------------------

;;; (s/def ::spacetime-event (s/tuple ::pid ::virtual-time))
;;; (s/def ::source-spacetime-event      ::spacetime-event)
;;; (s/def ::destination-spacetime-event ::spacetime-event)

;;; ----------------------------------------------------------------------------------

;;; The names of "private-ish" functions begin with a hyphen. Such functions may
;;; still be called, say for testing, without the fully qualified
;;; namespace-and-var syntax, but the hyphen reminds users that private-ish
;;; functions are part of the implementation rather than of the interface.

;;; ----------------------------------------------------------------------------------

;;; Time Warp is a Virtual-Time Operating System. It uses some abbreviated
;;; nomenclature traditional in operating systems like "mid" for "message-id,"
;;; "pid" for "process-id," and "pcb" for "process-control block." We endeavor
;;; to make the code self-explanatory, with a few concessions to shorter names.

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

(comment ---- MESSAGE AND PROCESS IDS -----------------------------------)

;;; We don't really need more elaborate types for ::mid and ::pid because they
;;; don't support an interesting protocol. The only possible benefit seems
;;; prevention of confusion between the two, as in using an ::mid where a ::pid
;;; is expected or vice versa. The risk does not seem greater than the overhead
;;; of an additional layer of type information. 

(s/def ::mid  uuid?)
(s/def ::pid  uuid?)

(def -new-mid new-uuid)
(def -new-pid new-uuid)

(comment ---- VIRTUAL TIME ----------------------------------------------)
;; __   ___     _             _   _____ _
;; \ \ / (_)_ _| |_ _  _ __ _| | |_   _(_)_ __  ___
;;  \ V /| | '_|  _| || / _` | |   | | | | '  \/ -_)
;;   \_/ |_|_|  \__|\_,_\__,_|_|   |_| |_|_|_|_\___|

;;; A protocol is natural packaging for an abstract data type (ADT) in Clojure,
;;; even without a need for polymorphism. Thus we define a VirtualTimeT
;;; protocol. The final "T" in the name is a concession to the "_t" convention
;;; in C++ for denoting "types." Protocols are like definitions of abstract
;;; types or interfaces.

(defprotocol VirtualTimeT
  "A number with two distinguished values for plus and minus infinity. Minus
  infinity is less than any virtual time other than minus infinity. Plus
  infinity is greater than any virtual time other than plus infinity.

  Virtual times are totally ordered, and this is the critical difference to real
  times, which are only partially ordered. That means that given two real times,
  we cannot always tell whether one is less than the other: not every pair is a
  member of the relation (set of pairs) that constitute the \"less-than\"
  relation. But with two virtual times, we can always tell whether one is less
  than the other or equal to the other (every pair is in the relation). This
  definition of \"total order\" is enshrined in a \"compare\" function that
  Clojure uses to sort a \"priority map.\" See
  https://github.com/clojure/data.priority-map."
  (vt-lt [this-vt that-vt])
  (vt-le [this-vt that-vt])
  (vt-eq [this-vt that-vt]))

(defn -vt-compare-lt [vt1 vt2]
  (case (:vt vt1)
    :vt-negative-infinity
    (case (:vt vt2)
      :vt-negative-infinity false
      #_otherwise true)

    :vt-positive-infinity
    false

    ;; otherwise: vt1 is a number.
    (case (:vt vt2)
      :vt-positive-infinity true
      :vt-negative-infinity false
      (< (:vt vt1) (:vt vt2)))))

;;; The virtual-time record type implements VirtualTimeT protocol.

(defrecord virtual-time [vt]
  VirtualTimeT
  (vt-lt [this that] (-vt-compare-lt this that))
  (vt-eq [this that] (= this that))
  (vt-le [this that] (or (vt-eq this that) (vt-lt this that))))

;;; A couple of global variables.

(def vt-negative-infinity (virtual-time. :vt-negative-infinity))
(def vt-positive-infinity (virtual-time. :vt-positive-infinity))

;;; Generators for specs and tests.

(def vt-number-gen
  (gen/bind
   (gen/large-integer)
   (fn [vt] (gen/return (virtual-time. vt)))))

(def vt-negative-infinity-gen
  (gen/return (virtual-time. :vt-negative-infinity)))

(def vt-positive-infinity-gen
  (gen/return (virtual-time. :vt-positive-infinity)))

;;; Now we have enough to define a spec for virtual time. We could just say that
;;; a virtual-time is any instance of the virtual-time type, but that's circular
;;; and therefore deficient; worse, it doesn't allow alternative implementations
;;; of the protocol. It's better to actually articulate the spec in terms of the
;;; required values. Thus we don't just say "#(instance?
;;; time_warp.core.virtual-time %)."

(s/def ::virtual-time
  (s/with-gen
    ;; A virtual time is either a number or one of the two distinguished values,
    ;; vt-minus-infinity and vt-plus-infinity.

    ;; The spec combinator "s/or" requires us to furnish "conformance tags,"
    ;; which Clojure propagates downstream to other spec generators so that they
    ;; can distinguish the cases. These conformance tags are redundant for us
    ;; because our distinct cases distinguish themselves and are handled
    ;; entirely in the VirtualTimeT protocol. Therefore, we need to strip off
    ;; the conformance tags so that downstream generators, like that for
    ;; ::input-queue, can manipulate virtual times in a natural fashion. We
    ;; strip off the conformance tags with an idiom in clojure.spec: "s/and"
    ;; supplies a place for a "conformer" function. A conformer function defines
    ;; the conformance values returned by the generator of this spec to other
    ;; generators. Our conformer function simply strips off the tags; i.e., it's
    ;; "second." The idiom is to define the spec with "s/and" and a single item:
    ;; in our case, the "s/or" of the three possibilities.
    (s/and
     (s/or
      :minus-infinity #(vt-eq % :vt-negative-infinity)
      :plus-infinity  #(vt-eq % :vt-positive-infinity)
      :number         #(number? (:vt %)))
     (s/conformer second))
    ;; We'd like most values generated in tests to be numerical, with the
    ;; occasional infinity for spice. Adjust these frequencies to taste.
    #(gen/frequency [[98 vt-number-gen]
                     [ 1 vt-negative-infinity-gen]
                     [ 1 vt-positive-infinity-gen]])))

;;; In the REPL, try (s/exercise ::virtual-time)

;;; EXPERIMENTAL --------------------------------------------------------)

;;; An experimental view of the Time-Warp Universe is as a mesh of causally
;;; connected spacetime events.

(s/def ::spacetime-event (s/tuple ::pid ::virtual-time))

(comment ---- MESSAGE ---------------------------------------------------)
;;  __  __
;; |  \/  |___ ______ __ _ __ _ ___
;; | |\/| / -_|_-<_-</ _` / _` / -_)
;; |_|  |_\___/__/__/\__,_\__, \___|
;;                        |___/

;;; In classic Time Warp, the sender (space coordinate) and send-time (time
;;; coordinate) are separated; ditto for receivers and receive-times.

(s/def ::sender       ::pid)
(s/def ::send-time    ::virtual-time)
(s/def ::receiver     ::pid)
(s/def ::receive-time ::virtual-time)
(s/def ::body         any?)
(s/def ::sign         #{-1 1})
(s/def ::message-id   ::mid)

;;; An alternative, experimental view is that space and time coordinates are
;;; parts of a composite "sspacetime-event" structure.For the experimental
;;; spacetime mesh.

(s/def ::source-spacetime-event      ::spacetime-event)
(s/def ::destination-spacetime-event ::spacetime-event)

;;; Messages with receive-time in the virtual past or present are of theoretical
;;; interest only. We define messages with arbitrary send and receive times as
;;; "potentially acausal."

;;; See https://clojure.org/guides/spec and search for "s/keys" on that page for
;;; specification of the "s/keys" function. It lets us define a message as a
;;; hashmap that must contain the keys listed. The "-un" qualifier recognizes
;;; unqualified keywords as keys in the hashmap. Quoting the cited guide: "Much
;;; existing Clojure code does not use maps with namespaced keys and so keys can
;;; also specify :req-un and :opt-un for required and optional unqualified keys.
;;; These variants specify namespaced keys used to find their specification, but
;;; the map only checks for the unqualified version of the keys."

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

(defn -messages-contents-match-but-for-id
  "True iff messages match in all attributes except id; needed for lazy
  cancellation."
  [m1 m2]
  (and (=     (:sender       m1) (:sender       m2))
       (vt-eq (:send-time    m1) (:send-time    m2))
       (=     (:receiver     m1) (:receiver     m2))
       (vt-eq (:receive-time m1) (:receive-time m2))
       (=     (:body         m1) (:body         m2))
       (=     (:sign         m1) (:sign         m2))
       (not (= (:message-id m1) (:message-id m2)))))

(defn -message-flip-sign
  "Needed for creating message pairs."
  [msg]
  (assoc msg :sign (- (:sign msg))))

;;; The following record type implements the protocol.

(defrecord tw-message
    [sender   send-time
     receiver receive-time
     body     sign
     message-id]
  MessageT

  (match-ids-opposite-signs [m1 m2]
    (-messages-match-ids-with-opposite-signs m1 m2))

  (match-but-id [m1 m2] (-messages-contents-match-but-for-id m1 m2))

  (flip-sign [m] (-message-flip-sign m)))

;;; The following "factory function" provides mnemonic keyword arguments for the
;;; default constructor automatically generated by "defrecord."

(defn -make-message
  "Produce a positive message with a fresh, random id and the other attributes
  given by arguments."
  [{:keys [sender   send-time
           receiver receive-time
           body     sign
           message-id]}]
  (tw-message. sender   send-time
               receiver receive-time
               body     sign
               message-id))

;;; See the test file for some conformance examples. An acausal message has
;;; send-time less than or equal to receive time.

(s/def ::potentially-acausal-message-hashmap
  (s/keys :req-un [::sender     ::send-time
                   ::receiver   ::receive-time
                   ::body       ::sign
                   ::message-id ]))

;;; Time-Warp Classic only allows causal messages. We call them just "messages."
;;; Note that a message hashmap wrapped in a defrecord, while acting mostly like
;;; a hashmap, it does not satisfy an s/keys spec. Therefore, we insert an extra
;;; layer that just generates and filters hashmap. TODO: consult the sages
;;; whether there is a better way to fulfill the protocol-record-spec pattern.

(s/def ::message
  (s/with-gen
    (s/and ;; ::potentially-acausal-message-hashmap
           #(vt-lt (:send-time %) (:receive-time %)))
    #(gen/fmap -make-message (s/gen ::potentially-acausal-message-hashmap))))

;;; In the REPL, try (s/exercise ::message)

;;; Input messages are always positive.

(s/def ::input-message
  (s/and ::message #(= (:sign %) 1)))

;;; Output messages are always negative.

(s/def ::output-message
  (s/and ::message #(= (:sign %) -1)))

(comment ---- MESSAGE PAIRS ---------------------------------------------)
;;  __  __                            ___      _
;; |  \/  |___ ______ __ _ __ _ ___  | _ \__ _(_)_ _ ___
;; | |\/| / -_|_-<_-</ _` / _` / -_) |  _/ _` | | '_(_-<
;; |_|  |_\___/__/__/\__,_\__, \___| |_| \__,_|_|_| /__/
;;                        |___/

(s/def ::message-pair
  (s/with-gen
    (s/and (s/tuple ::message ::message)
           (fn [[m1 m2]] (=     (:message-id   m1) (:message-id   m2)))
           (fn [[m1 m2]] (=     (:sender       m1) (:sender       m2)))
           (fn [[m1 m2]] (vt-eq (:send-time    m1) (:send-time    m2)))
           (fn [[m1 m2]] (=     (:receiver     m1) (:receiver     m2)))
           (fn [[m1 m2]] (vt-eq (:receive-time m1) (:receive-time m2)))
           (fn [[m1 m2]] (=     (:body         m1) (:body         m2)))
           (fn [[m1 m2]] (=     (:sign m1)  1))
           (fn [[m1 m2]] (=     (:sign m2) -1)))
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
  (let [m (-make-message :sender       sender
                         :send-time    send-time
                         :receiver     receiver
                         :receive-time receive-time
                         :body         body)]
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
  ;; that the virtual time in each pair matches either the receive-time or the
  ;; send-time of the message in the same pair. The matching virtual times are
  ;; receive times from an input queue and send times in an output queue.
  (fetch-bundle   [q  vt])
  ;; Insert a message in a queue, potentially annihilating its antimessage.
  (insert-message [q   m])
  ;; Delete a message given only its mid.
  (delete-message-by-mid [q mid])
  ;; TODO more ...
  )

;;; We write a spec for priority maps just so we can write a test generator.

(s/def ::priority-map
  (s/with-gen
    #(instance? (class (priority-map)) %)
    ;; The generator is a function that returns an empty priority map.
    (constantly (gen/return (priority-map)))))

(defn -every-value-a-virtual-time
  "Check that every value in a sequence of key-value pairs is a valid virtual
  time."
  [xs]
  (every? #(s/valid? ::virtual-time %) (map second xs)))

(defn -make-empty-vt-queue
  "A virtual-time queue is a priority map of [object vt] pairs, sorted by the
  virtual-time total ordering function \"-vt-compare-lt.\""
  []
  (priority-map-by -vt-compare-lt))

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
;;; ~~> {"a", {:mid "a", :sender ...}, "b", {:mid "b", :sender ...}}
;;;
;;; Deleting a message from an input queue is supported by a transactional
;;; "dissoc" on both structures.

;;; An input queue is inherently stateful, so we will coordinate transactions
;;; with refs, supporting multithreaded operation in a single processor node
;;; (see https://clojure.org/reference/refs).

;;; TODO: For an initial pass, we'll just do linear searches with "filter". They
;;; may be faster than principled data structures for small queues anyway.

(defmacro dump [x]
  `(let [x# ~x]
     (do (println '~x "~~> ")
         (clojure.pprint/pprint x#)
         (println "")
         x#)))

(defrecord input-queue [iq-priority-map]
  MessageQueueT
  ;; Note: {:vt 3} does not equal (virtual-time. 3), so we must dig out the
  ;; values from the virtual-time maps. TODO: reconsider record / protocol
  ;; implementations: too heavyweight?

  ;; fetch-bundle does not produces a queue, but rather a straight collection of
  ;; messages.
  (fetch-bundle          [q  vt]
    (->> (filter
          (fn [[msg vt2]] (vt-eq vt vt2))
          (:iq-priority-map q))
         (map first)))
  ;; TODO: insert-message is improperly exposed to insertion of duplicates.
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

(s/def ::input-queue
  (s/with-gen
    (s/and ::priority-map
           -every-value-a-virtual-time
           #(every? (fn [[msg vt]]
                      (vt-eq vt (:receive-time msg)))
                    %))
    #(s/gen (s/coll-of
             ::input-message-and-receive-time-pair
             :into (-make-empty-vt-queue) :gen-max 50))))

;;; Try (first (gen/sample (s/gen ::input-queue) 1)) in the REPL to get a
;;; randomly generated input queue.

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
     (fn [[msg vt2]] (vt-eq vt vt2))
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
                      (vt-eq vt (:send-time msg)))
                    %))
    #(s/gen (s/coll-of
             ::output-message-and-send-time-pair
             :into (-make-empty-vt-queue) :gen-max 50))))

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
               (-make-empty-vt-queue) (-make-empty-vt-queue) (-make-empty-vt-queue)
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
