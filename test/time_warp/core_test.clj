(ns time-warp.core-test
  (:require [clojure.test                    :refer      :all]
            [clojure.test.check              :as           tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties   :as         prop]
            [clojure.spec                    :as            s]
            [clojure.spec.gen                :as          gen]
            [orchestra.spec.test             :as        otest] ; see https://goo.gl/73c9JP
            [time-warp.core                  :refer      :all]))

;;; see https://clojure.github.io/clojure/branch-master/clojure.spec-api.html,
;;; https://goo.gl/dyu59U

(def vt-gen (gen/one-of [vt-number-gen
                         vt-negative-infinity-gen
                         vt-positive-infinity-gen]))

;; __   ___     _             _   _____ _
;; \ \ / (_)_ _| |_ _  _ __ _| | |_   _(_)_ __  ___
;;  \ V /| | '_|  _| || / _` | |   | | | | '  \/ -_)
;;   \_/ |_|_|  \__|\_,_\__,_|_|   |_| |_|_|_|_\___|
;;; 1.
(defspec minus-infinity-less-than-all-but-minus-infinity
  100
  (prop/for-all
   [vt vt-gen]
   (if (not= (:vt vt) :vt-negative-infinity)
     (vt-lt vt-negative-infinity vt)
     true)))
;;; 2.
(defspec plus-infinity-not-less-than-any
  100
  (prop/for-all
   [vt vt-gen]
   (not (vt-lt vt-positive-infinity vt))))

;;   ___                    _   __  __
;;  / __|__ _ _  _ ___ __ _| | |  \/  |___ ______ __ _ __ _ ___ ___
;; | (__/ _` | || (_-</ _` | | | |\/| / -_|_-<_-</ _` / _` / -_|_-<
;;  \___\__,_|\_,_/__/\__,_|_| |_|  |_\___/__/__/\__,_\__, \___/__/
;;                                                    |___/
;;; 3.
(deftest hand-constructed-message-expected-to-be-invalid
  ;; Must refer to spec by fully-qualified keyword.
  (is (not (s/valid? :time-warp.core/message
                     (-make-message
                      {:sender       (new-uuid)
                       ;; Must refer to defrecord by snake-cased java classpath
                       ;; dot notation.
                       :send-time    (time_warp.core.virtual-time. 42)
                       :receiver     (new-uuid)
                       ;; Receive time is less than send time, therefore message
                       ;; is acausal and invalid.
                       :receive-time (time_warp.core.virtual-time. 41)
                       :sign         1
                       :body         ["foo" "bar" "baz" "qux"]
                       :message-id   (new-uuid)})))))
;;; 4.
(deftest hand-constructed-message-conforms-to-spec
  (is (s/valid? :time-warp.core/message
                (-make-message
                 {:sender       (new-uuid)
                  :send-time    (time_warp.core.virtual-time. 42)
                  :receiver     (new-uuid)
                  ;; This one is OK.
                  :receive-time (time_warp.core.virtual-time. 43)
                  :body         ["foo" "bar" "baz" "qux"]
                  :sign         1
                  :message-id   (new-uuid)}))))
;;; 5.
(defspec send-time-lt-receive-time-for-causal-messages
  10
  (prop/for-all
   [m (s/gen :time-warp.core/message)]
   (vt-lt (:send-time m) (:receive-time m))))

;;; 6.
(defn hand-constructed-message []
  (-make-message
   {:sender       (new-uuid)
    :send-time    (time_warp.core.virtual-time. 42)
    :receiver     (new-uuid)
    :receive-time (time_warp.core.virtual-time. 43)
    :body         (random-sample 0.5 ["foo" "bar" "baz" "qux"])
    :sign         (rand-nth [-1 1])
    :message-id   (new-uuid)}))

(deftest hand-constructed-message-passes-match-but-id-with-modified-self
  (is (let [m (hand-constructed-message)]
        (.match-but-id m (assoc m :message-id (new-uuid))))))
;;; 7.
(deftest hand-constructed-message-passes-match-ids-opposite-signs
  (is (let [m (hand-constructed-message)]
        (.match-ids-opposite-signs m (.flip-sign m)))))
;;; 8.
(deftest tw-message-record-conforms-to-hashmap-spec
  (is (s/valid? :time-warp.core/message
                (hand-constructed-message))))

;;  __  __                            ___      _
;; |  \/  |___ ______ __ _ __ _ ___  | _ \__ _(_)_ _ ___
;; | |\/| / -_|_-<_-</ _` / _` / -_) |  _/ _` | | '_(_-<
;; |_|  |_\___/__/__/\__,_\__, \___| |_| \__,_|_|_| /__/
;;                        |___/
;;; 9.
(defspec message-pairs-identical-but-for-sign
  10
  (prop/for-all
   [[pos neg] (s/gen :time-warp.core/message-pair)]
   (and (=  1 (:sign pos))
        (= -1 (:sign neg))
        (= (:body         pos) (:body         neg))
        (= (:sender       pos) (:sender       neg))
        (= (:send-time    pos) (:send-time    neg))
        (= (:receiver     pos) (:receiver     neg))
        (= (:receive-time pos) (:receive-time neg)))))

;; __   ___     _             _    _____ _              ___
;; \ \ / (_)_ _| |_ _  _ __ _| |__|_   _(_)_ __  ___   / _ \ _  _ ___ _  _ ___
;;  \ V /| | '_|  _| || / _` | |___|| | | | '  \/ -_) | (_) | || / -_) || / -_)
;;   \_/ |_|_|  \__|\_,_\__,_|_|    |_| |_|_|_|_\___|  \__\_\\_,_\___|\_,_\___|

;;; 10.
(defspec input-queue-is-monotonic-in-virtual-time
  10 ;; This takes a long time at 99
  (prop/for-all
   [msg-vt-pairs (s/gen :time-warp.core/input-queue)]
   (let [times (map second msg-vt-pairs)
         vt-vt-pairs (partition 2 1 times)] ; constructs adjacent pairs
     (every? (partial apply #(vt-le %1 %2))
             vt-vt-pairs))))
;;; 11.
(defspec all-messages-in-an-input-queue-are-positive
  10
  (prop/for-all
   [msg-vt-pairs (s/gen :time-warp.core/input-queue)]
   (every? (fn [[msg vt]] (= 1 (:sign msg)))
           msg-vt-pairs)))
;;; 12.
(defspec input-queue-values-equal-receive-times
  10
  (prop/for-all
   [msg-vt-pairs (s/gen :time-warp.core/input-queue)]
   (every? (fn [[msg vt]] (vt-eq vt (:receive-time msg)))
           msg-vt-pairs)))
;;; 13.
(defspec all-messages-in-an-output-queue-are-negative
  10
  (prop/for-all
   [msg-vt-pairs (s/gen :time-warp.core/output-queue)]
   (every? (fn [[msg vt]] (= -1 (:sign msg)))
           msg-vt-pairs)))

;;; Here is a particular, large input queue created by test.check.
;;; I write some point-like, example tests on it below.

(def iq-1
  (time_warp.core.input-queue.
   (into
    (-make-empty-vt-queue)
    {(-make-message
      {:sender #uuid "d20f91ba-582f-4c64-a65f-f7d1d7892799",
       :send-time (time_warp.core.virtual-time. :vt-negative-infinity),
       :receiver #uuid "4bf42ec1-02ec-40c6-97f1-86fa92017593",
       :receive-time (time_warp.core.virtual-time. -3),
       :body `#:if.*0._{C+? :_.xwNG/z!f3},
       :sign 1,
       :message-id #uuid "73cb5304-11a7-4a0e-bfeb-334e8669452a"})
     (time_warp.core.virtual-time. -3),
     (-make-message
      {:sender #uuid "25b6600e-ef37-4b2e-bf9b-44dfb130e19f",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "e699570a-43e7-4ce3-a864-c172efbd0660",
       :receive-time (time_warp.core.virtual-time. -1),
       :body (),
       :sign 1,
       :message-id #uuid "d77ff22c-b3a2-415d-ab87-3d164f3f277f"})
     (time_warp.core.virtual-time. -1),
     (-make-message
      {:sender #uuid "d5bd0e65-d333-462f-acae-a7f0e214e8ad",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "fe18aad3-4059-4226-8d4b-b5515ab5a81e",
       :receive-time (time_warp.core.virtual-time. -1),
       :body nil,
       :sign 1,
       :message-id #uuid "a760ff22-5a21-4b1d-886e-7d978099f900"})
     (time_warp.core.virtual-time. -1),
     (-make-message
      {:sender #uuid "2e00a843-99b7-4859-aee8-bfa7a4adca4b",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "d68171fd-1a10-4c45-a251-676a32a80cd6",
       :receive-time (time_warp.core.virtual-time. -1),
       :body nil,
       :sign 1,
       :message-id #uuid "a014f91e-7584-42e1-b7da-347057f1b47e"})
     (time_warp.core.virtual-time. -1),
     (-make-message
      {:sender #uuid "f38c1700-d89b-4bc0-8a59-b85e0adbaef8",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "5fcb2ed1-dd3e-44e4-a7b0-1bf2843ccc05",
       :receive-time (time_warp.core.virtual-time. -1),
       :body (),
       :sign 1,
       :message-id #uuid "8b33a968-cf5d-4da2-a624-a55d282688bd"})
     (time_warp.core.virtual-time. -1),
     (-make-message
      {:sender #uuid "1c035b30-079f-4440-b958-e27fe7fb063c",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "c701d604-9ac6-4b51-a6ee-5c5a4724e7d1",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "cc1a6d8b-d7a3-4510-9b95-e0360e0c66f6"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "7a82f3a3-8d80-456c-b59d-21f89d807ce5",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "9aa8cab5-b6a9-48c2-8c1c-283ade4fd4b0",
       :receive-time (time_warp.core.virtual-time. 0),
       :body [{}],
       :sign 1,
       :message-id #uuid "9a0470ac-2c94-45f9-8d52-2df88690e609"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "51723fe2-acae-4f84-b7e7-2d776b58a142",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "f79219cd-9e3f-4465-a225-bc599bd462b1",
       :receive-time (time_warp.core.virtual-time. 0),
       :body (),
       :sign 1,
       :message-id #uuid "ac11d6aa-2849-4b27-abde-d93dd2ced9eb"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "e9c29cb3-8d99-4078-82cf-d511c7abfe08",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "f02553db-7dad-406e-80d2-b4bca3ae6577",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "ac7c6f99-e8e0-4a14-ab3f-d51c7bbec234"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "cad9e49c-5f73-4709-acb7-4d0bf81ebdca",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "b2cd7304-4758-42a2-a2be-af5c5c1c2d7f",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "02404c50-48b8-4457-892d-0448a9584310"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "54ac0818-d1b0-4c37-8e93-30a8e031fe09",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "42d27b55-70d8-4ba0-86d4-beeee21c44c3",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "5d68a350-72cc-4c4a-a781-660985da269c"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "d72c762f-8ee2-4617-8f5e-f2b46286f8e2",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "975735de-423f-4eec-8b4a-f1c2cb3750e6",
       :receive-time (time_warp.core.virtual-time. 0),
       :body (),
       :sign 1,
       :message-id #uuid "470e5eb5-ec5d-41b9-9fe0-67e794cd394b"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "057992f2-8b6b-4340-bf3e-4145caad89a4",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "63449b83-cbc4-43d0-b9f4-d2bca6eb2c6f",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "e1b8625d-4dbf-458d-9270-05cb49c4b058"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "3dbdc293-afc3-4346-a339-f5ec55152142",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "346719d5-90ad-4d0c-a351-6b639bdae532",
       :receive-time (time_warp.core.virtual-time. 0),
       :body {},
       :sign 1,
       :message-id #uuid "82fbebdb-4412-419d-8276-1f39ccb89b93"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "8f69c3db-7bd1-4ee3-b9e2-949bd03f352a",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "bd2b10bf-997f-4db1-a5d3-a7ff82e7e8df",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "5f4cdc28-4f6e-4b75-9312-a6163a28aa8a"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "12b1dff8-e1c7-4f33-a8d0-17d784fb27c2",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "08891245-ed7d-423a-a072-43f7788c859b",
       :receive-time (time_warp.core.virtual-time. 0),
       :body {},
       :sign 1,
       :message-id #uuid "52ce30a1-03a2-454d-9aa4-f00c82ed8589"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "f628f526-3043-418b-a04a-167c55a0ece8",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "5235d3fd-c4f1-4022-9c86-5c7d33a03424",
       :receive-time (time_warp.core.virtual-time. 0),
       :body (),
       :sign 1,
       :message-id #uuid "922f4b0a-2309-4df9-9be4-42c9d672b67e"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "99a91ffb-3ed1-456d-9c92-bc8a4cbcaa7a",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "451b11f7-f8e0-42d8-91b3-adddaa001fae",
       :receive-time (time_warp.core.virtual-time. 0),
       :body '(()),
       :sign 1,
       :message-id #uuid "03188fec-7af7-41f5-96b5-e677748987bd"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "a7dc706e-0629-40d3-8b61-447425271f71",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "f0e192b4-ac44-43d2-964a-4f8118aff170",
       :receive-time (time_warp.core.virtual-time. 0),
       :body [],
       :sign 1,
       :message-id #uuid "fa6bd213-45ef-4bcb-9b62-e6764a62a11e"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "38dad945-6f8b-4ace-b6b4-cdeb8da6a7fb",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "9779d934-146a-4665-81c4-7adee6daf737",
       :receive-time (time_warp.core.virtual-time. 0),
       :body {},
       :sign 1,
       :message-id #uuid "fe997ba6-15d4-42e0-9db9-990913433ef3"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "0bb695d5-bfd2-4a39-8fe5-1cf05d356a86",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "260d5dae-d96d-4038-9e66-8d9f2c4d5505",
       :receive-time (time_warp.core.virtual-time. 0),
       :body (),
       :sign 1,
       :message-id #uuid "fd31391a-4894-499c-be67-35564019843a"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "c0f07bc9-8164-4909-bebf-50cde67ccb8a",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "5574d4da-da7f-4204-a8b3-92f484e7f495",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "17e1ebe9-d7e8-4866-998b-4c482090ae5c"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "45762a43-bb53-4b49-96f0-053a2aed68d4",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "af80c1e8-ffdd-4842-9656-c89e5749ed50",
       :receive-time (time_warp.core.virtual-time. 0),
       :body [],
       :sign 1,
       :message-id #uuid "fa519d58-3acc-4708-86f7-1e169c0ede0c"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "66f79b58-4ef4-434a-96b0-c78e5aa4cb2e",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "654fe4b1-9c34-4205-96b8-32d64f51e2d5",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "08fe49bd-312f-4ba0-8857-2e1300c15546"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "f36e2b8e-3424-459b-b075-0a291758dd94",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "7a2e37fd-5ebc-475b-a1b8-15abd8be2c14",
       :receive-time (time_warp.core.virtual-time. 0),
       :body (),
       :sign 1,
       :message-id #uuid "25211d73-6ed8-4d1d-a901-2e6c10fe034e"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "df0ab670-a542-415f-981d-e941313367ca",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "7cf3b78b-56d3-4c50-bca3-6e5581a1b9d4",
       :receive-time (time_warp.core.virtual-time. 0),
       :body [-3/4 1],
       :sign 1,
       :message-id #uuid "7592169e-ccf7-4540-a552-43d3f2df91d1"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "cab7623a-6122-45be-987c-1078ebc98ff2",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "be526d72-d9f1-4537-a385-f5553d76986e",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "f319d29b-4919-4f9a-ba42-386f25277bc5"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "0db747a4-32e9-411b-94f6-85108e066f14",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "c613f9ba-9710-4f76-9fbb-40560f3e2097",
       :receive-time (time_warp.core.virtual-time. 0),
       :body [{false true}],
       :sign 1,
       :message-id #uuid "90a564e9-fdf4-47ce-acf7-6674d79a4812"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "542abec3-bd79-4130-b305-8d6aedb041b1",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "85260915-0f88-48a9-b0bf-3cefb9991054",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "49c3f91f-9d85-46af-a374-491f6aa0dbe6"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "988a9d14-dd9c-4a85-8ceb-6b155140f8cd",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "ce1155d9-55a8-4549-a974-13932424ea9b",
       :receive-time (time_warp.core.virtual-time. 0),
       :body [],
       :sign 1,
       :message-id #uuid "135d6c4f-a20f-4dc3-9847-b52b18e66691"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "5ac22cc5-afb5-489a-8c24-128536c1a275",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "189f777e-bcaf-4ade-b4ad-ada9785ca1b5",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "62b0d20b-8b95-495c-8a3e-bfd45205bb53"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "a3d3ca3f-2062-4ce5-9fe7-27d6dc9f6824",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "b49026c8-b93d-4b64-b86a-1ee647d295b0",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "25c004fa-df63-4ce7-aa02-65b0fed2c0c3"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "01932cd9-c0b7-4625-beb2-9210839a3c4d",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "b24fbcda-df4e-4195-95cb-ed4038d5c73e",
       :receive-time (time_warp.core.virtual-time. 0),
       :body {},
       :sign 1,
       :message-id #uuid "fef87973-1924-489b-8a88-616de6ccb8b9"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "e7bb3b2b-b70d-4c01-8958-132dfc9979fb",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "4ae422bb-2bcd-4811-8622-9124f9e9eb5b",
       :receive-time (time_warp.core.virtual-time. 0),
       :body (),
       :sign 1,
       :message-id #uuid "cbd5af9b-7788-4660-bcd4-a8abc2f16597"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "f8013422-b303-427d-a29a-7e57f1cc318c",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "1d9e0d8b-d373-4340-88c7-b38860461bb1",
       :receive-time (time_warp.core.virtual-time. 0),
       :body [],
       :sign 1,
       :message-id #uuid "f846fb70-7b88-4e9b-9149-d59cb6bd48a1"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "52dcfebe-5c48-42ee-ac0b-ee136f24808c",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "9e23bbc0-6449-42dc-88ae-41f05b033809",
       :receive-time (time_warp.core.virtual-time. 0),
       :body nil,
       :sign 1,
       :message-id #uuid "4bd1bd60-4097-41dc-b0b3-72521baf8d48"})
     (time_warp.core.virtual-time. 0),
     (-make-message
      {:sender #uuid "2c56971e-7ff5-4b7c-8906-49fa5540b9c7",
       :send-time (time_warp.core.virtual-time. 0),
       :receiver #uuid "c2df23d1-fb6a-4258-b53d-af90e147c22b",
       :receive-time (time_warp.core.virtual-time. 1),
       :body nil,
       :sign 1,
       :message-id #uuid "1f893b6f-dc77-4bd8-9040-a287d811f365"})
     (time_warp.core.virtual-time. 1),
     (-make-message
      {:sender #uuid "79e85fe3-0749-4bb8-97ba-7a903780adb6",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "d72ce1ef-8504-4a8f-b106-38534f69e77c",
       :receive-time (time_warp.core.virtual-time. 1),
       :body nil,
       :sign 1,
       :message-id #uuid "38bf3789-b251-406b-9dcb-df5f228b987a"})
     (time_warp.core.virtual-time. 1),
     (-make-message
      {:sender #uuid "ca3f4937-154b-4a1c-ba75-9f28c60d9f25",
       :send-time (time_warp.core.virtual-time. 0),
       :receiver #uuid "fdfec549-506a-47db-a7da-4b890e18aa6e",
       :receive-time (time_warp.core.virtual-time. 1),
       :body '([()]),
       :sign 1,
       :message-id #uuid "0737dbe1-6646-4521-8446-4a7468f0717e"})
     (time_warp.core.virtual-time. 1),
     (-make-message
      {:sender #uuid "a13dd09a-6a1d-45bb-bddf-9764f61153db",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "10c00b94-e9a8-47b2-90ac-53d88e408535",
       :receive-time (time_warp.core.virtual-time. 1),
       :body [[[]]],
       :sign 1,
       :message-id #uuid "e0e06851-842c-4aac-8d56-d1fb3fdca21a"})
     (time_warp.core.virtual-time. 1),
     (-make-message
      {:sender #uuid "6b3bda43-bfeb-415e-92bd-3dc3bd6df074",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "25ea4c04-4086-4d0f-bbae-e240f54288a5",
       :receive-time (time_warp.core.virtual-time. 1),
       :body {},
       :sign 1,
       :message-id #uuid "cd566caf-2bc5-437a-ba3b-bb20bc761e90"})
     (time_warp.core.virtual-time. 1),
     (-make-message
      {:sender #uuid "f481af03-478f-4e61-95fa-0739e9c91904",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "3cd84317-9a04-4d53-9bff-1adb0684c1ad",
       :receive-time (time_warp.core.virtual-time. 1),
       :body {},
       :sign 1,
       :message-id #uuid "84def085-782a-42ba-b605-9804522e41d6"})
     (time_warp.core.virtual-time. 1),
     (-make-message
      {:sender #uuid "b3511708-8af7-41a9-90b3-69924c16f5ab",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "7ac96b46-d2df-4805-822f-c0d3e7defd1e",
       :receive-time (time_warp.core.virtual-time. 1),
       :body [],
       :sign 1,
       :message-id #uuid "f9b0c444-4504-4984-9f87-7012a4c27761"})
     (time_warp.core.virtual-time. 1),
     (-make-message
      {:sender #uuid "e19941a4-e52c-4b15-8fe5-d664aa3007d3",
       :send-time (time_warp.core.virtual-time. -4),
       :receiver #uuid "d04daf5b-dc77-4e05-8dc9-bf9243ec9534",
       :receive-time (time_warp.core.virtual-time. 2),
       :body nil,
       :sign 1,
       :message-id #uuid "c030b79b-1981-4fd0-aef0-4e460d7d0c7c"})
     (time_warp.core.virtual-time. 2),
     (-make-message
      {:sender #uuid "af0fec22-8f17-41ec-ac81-09758f8fff64",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "11740d63-2caf-4962-aaec-97e69a25ab27",
       :receive-time (time_warp.core.virtual-time. 3),
       :body [{}],
       :sign 1,
       :message-id #uuid "f600378c-4589-4b53-849a-a9fcb940ff6f"})
     (time_warp.core.virtual-time. 3),
     (-make-message
      {:sender #uuid "e6d19ace-65e8-437b-8b02-0cb32587c724",
       :send-time (time_warp.core.virtual-time. 0),
       :receiver #uuid "a6afa838-ddc4-4972-a147-7c6fecb59385",
       :receive-time (time_warp.core.virtual-time. 3),
       :body nil,
       :sign 1,
       :message-id #uuid "e4ce2511-18b5-4d62-a130-fbf8060196bb"})
     (time_warp.core.virtual-time. 3),
     (-make-message
      {:sender #uuid "26629509-c8df-4ab1-bae5-08beeebaedf5",
       :send-time (time_warp.core.virtual-time. 3),
       :receiver #uuid "9fa84c8a-1568-4c12-9979-44790e16f6ed",
       :receive-time (time_warp.core.virtual-time. 4),
       :body '([]),
       :sign 1,
       :message-id #uuid "45a686d6-6b62-47f4-9820-febefddabf16"})
     (time_warp.core.virtual-time. 4),
     (-make-message
      {:sender #uuid "57c7b440-e5f2-4c67-b516-bc280e68649f",
       :send-time (time_warp.core.virtual-time. -2),
       :receiver #uuid "9b9440de-9d0b-4a29-80e4-4676c14f384f",
       :receive-time (time_warp.core.virtual-time. 5),
       :body ['(\1)],
       :sign 1,
       :message-id #uuid "2ef42d60-5168-4e16-832c-6a1f51aaec50"})
     (time_warp.core.virtual-time. 5),
     (-make-message
      {:sender #uuid "d7c9c04c-25ba-4f78-9d99-9685812eb769",
       :send-time (time_warp.core.virtual-time. -1),
       :receiver #uuid "487347c8-2827-4a17-930d-dffd506c76cc",
       :receive-time (time_warp.core.virtual-time. :vt-positive-infinity),
       :body nil,
       :sign 1,
       :message-id #uuid "924c6b52-07d5-4598-be34-03e7c97e6745"})
     (time_warp.core.virtual-time. :vt-positive-infinity)})))
;;; 14.
(deftest two-messages-with-receive-time-3-in-iq-1
  (let [expected
        `(~(-make-message
           {:sender #uuid "af0fec22-8f17-41ec-ac81-09758f8fff64",
            :send-time (time_warp.core.virtual-time. -1),
            :receiver #uuid "11740d63-2caf-4962-aaec-97e69a25ab27",
            :receive-time (time_warp.core.virtual-time. 3),
            :body [{}],
            :sign 1,
            :message-id #uuid "f600378c-4589-4b53-849a-a9fcb940ff6f"})
          ~(-make-message
           {:sender #uuid "e6d19ace-65e8-437b-8b02-0cb32587c724",
            :send-time (time_warp.core.virtual-time. 0),
            :receiver #uuid "a6afa838-ddc4-4972-a147-7c6fecb59385",
            :receive-time (time_warp.core.virtual-time. 3),
            :body nil,
            :sign 1,
            :message-id #uuid "e4ce2511-18b5-4d62-a130-fbf8060196bb"})
          )]
    (is (= expected
           (fetch-bundle
            iq-1
            (time_warp.core.virtual-time. 3))))))
;;; 15.
(deftest thirty-one-messages-with-receive-time-0-in-iq-1
  (is (= 31 (count
             (fetch-bundle
              iq-1
              (time_warp.core.virtual-time. 0))))))
;;; 16.
(deftest one-message-with-receive-time-positive-infinity-in-iq-1
  (is (= 1 (count
            (fetch-bundle
             iq-1
             vt-positive-infinity)))))
;;; 17.
(deftest no-messages-with-receive-time-negative-infinity-in-iq-1
  (is (= 0 (count
            (fetch-bundle
             iq-1
             vt-negative-infinity)))))
;;; 18.
(deftest iq-1-is-monotonic-in-virtual-time
  (is (let [times (map second iq-1)
            vt-vt-pairs (partition 2 1 times)] ; constructs adjacent pairs
        (every? (partial apply #(vt-le %1 %2))
                vt-vt-pairs))))
;;; 19.
(deftest all-messages-in-iq-1-are-positive
  (is (every? (fn [[msg vt]] (= 1 (:sign msg)))
              (:iq-priority-map iq-1))))
;;; 20.
(deftest every-vt-value-equals-receive-time-in-iq-1
  (is (every? (fn [[msg vt]] (= (:receive-time msg) vt))
              (:iq-priority-map iq-1))))
;;; 21.
(deftest forty-nine-messages-in-iq-1
  (is (= 49 (count (:iq-priority-map iq-1)))))
;;; 22.
(deftest iq-1-has-a-priority-queue
  (is (instance? clojure.data.priority_map.PersistentPriorityMap
                 (:iq-priority-map iq-1))))
;;; 23.
(defspec every-vt-value-equals-receive-time-in-an-input-queue
  10
  (prop/for-all
   [msg-vt-pairs (s/gen :time-warp.core/input-queue)]
   (every? (fn [[msg vt]] (= (:receive-time msg) vt))
           msg-vt-pairs)))
;;; 24.
(deftest deleting-one-message-produces-a-queue-with-forty-eight-messages
  (is (= 48 (-> iq-1
                (delete-message-by-mid
                 #uuid "cbd5af9b-7788-4660-bcd4-a8abc2f16597")
                :iq-priority-map
                count))))
;;; 25.
(deftest deleting-a-junk-mid-leaves-the-queue-unmodified
  (is (= 48 (-> iq-1
                (delete-message-by-mid
                 #uuid "10301030-1030-0204-1030-020402040204")
                :iq-priority-map
                count))))
;;; 26.
(deftest deleting-all-messages-produces-empty-queue
  (is (= 0 (-> (reduce delete-message-by-mid
                       iq-1
                       (->> iq-1
                            :iq-priority-map
                            (map first)
                            (map :message-id)))
               :iq-priority-map
               count))))
;;; 27.
(defspec inserting-a-new-input-message-into-iq-1-increases-its-count-to-fifty
  10
  (prop/for-all
   [m (s/gen :time-warp.core/input-message)]
   (= 50 (-> iq-1
             (insert-message m)
             :iq-priority-map
             count))))

;;; 28.
(defspec all-message-ids-in-input-queue-are-unique
  10
  (prop/for-all
   [iq (s/gen :time-warp.core/input-queue)]
   (let [mid-set (reduce
                  (fn [set [msg vt]] (conj set (:message-id msg)))
                  #{}
                  (:iq-priority-map iq))]
     (= (count mid-set) (count (:iq-priority-map iq))))))

;;  ___                   _               _        _
;; | __|_ ___ __  ___ _ _(_)_ __  ___ _ _| |_ __ _| |
;; | _|\ \ / '_ \/ -_) '_| | '  \/ -_) ' \  _/ _` | |
;; |___/_\_\ .__/\___|_| |_|_|_|_\___|_||_\__\__,_|_|
;;         |_|
;;  _____                          _   _               _   ___                _
;; |_   _| _ __ _ _ _  ___ __ _ __| |_(_)___ _ _  __ _| | |_ _|_ _  _ __ _  _| |_
;;   | || '_/ _` | ' \(_-</ _` / _|  _| / _ \ ' \/ _` | |  | || ' \| '_ \ || |  _|
;;   |_||_| \__,_|_||_/__/\__,_\__|\__|_\___/_||_\__,_|_| |___|_||_| .__/\_,_|\__|
;;                                                                 |_|
;;   ___
;;  / _ \ _  _ ___ _  _ ___
;; | (_) | || / -_) || / -_)
;;  \__\_\\_,_\___|\_,_\___|


