; Copyright 2013 Relevance, Inc.
; Copyright 2014 Cognitect, Inc.

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(ns io.pedestal.http.body-params-test
  (:use io.pedestal.http.body-params
        clojure.pprint
        clojure.test
        clojure.repl)
  (:require [clojure.instant :as inst]
            [io.pedestal.impl.interceptor :as interceptor]))

(defn as-context [content-type ^String body]
  (let [body-reader (java.io.ByteArrayInputStream. (.getBytes body))]
    {:request {:content-type content-type
               :headers {"content-type" content-type}
               :body body-reader}}))

(def i (:enter (body-params)))

(def i-using-opts
  (-> (default-parser-map
        :edn-options {:readers {'inst inst/read-instant-timestamp}}
        :json-options {:key-fn nil})
      body-params :enter))

(deftest parses-json
  (let [json-context (as-context "application/json" "{ \"foo\": \"BAR\"}")
        new-context  (i json-context)
        new-request  (:request new-context)]
    (is (= (:json-params new-request) {:foo "BAR"}))))

(deftest parses-json-using-opts
  (let [json-context (as-context "application/json" "{ \"foo\": \"BAR\"}")
        new-context (i-using-opts json-context)
        new-request (:request new-context)]
    (is (= (:json-params new-request) {"foo" "BAR"}))))

(defn json-request
  [json-context options]
  (let [i (-> (default-parser-map :edn-options {} :json-options options)
              body-params :enter)
        new-context (i json-context)]
    (:request new-context)))

(deftest json-parser-supports-bigdec-option
  (letfn [(json-context [] (as-context "application/json" "{ \"bd\": 1.0001 }"))
           (json-params [options] (:json-params (json-request (json-context) options)))]
    (is (= (json-params {})
           {:bd 1.0001}))
    (is (= (json-params {:bigdec true})
           {:bd 1.0001M}))))

(deftest json-parser-supports-array-coercen-fn-option
  (letfn [(json-context [] (as-context "application/json" "{ \"a\": [1, 2, 3] }"))
           (json-params [options] (:json-params (json-request (json-context) options)))]
    (is (= (json-params {})
           {:a [1 2 3]}))
    (is (= (json-params {:array-coerce-fn (fn [name] #{})})
           {:a #{1 2 3}}))))

(deftest parses-form-data
  (let [form-context (as-context  "application/x-www-form-urlencoded" "foo=BAR")
        new-context  (i form-context)
        new-request  (:request new-context)]
    (is (= (:form-params new-request) {"foo" "BAR"}))))

(deftest parses-edn
  (let [edn-context (as-context "application/edn" "(i wish i [was in] eden)")
        new-context (i edn-context)
        new-request (:request new-context)]
    (is (= (:edn-params new-request) '(i wish i [was in] eden)))))

(deftest parses-edn-using-opts
  (let [edn-context (as-context "application/edn" "#inst \"1970-01-01T00:00:00.000-00:00\"")
        new-context (i-using-opts edn-context)
        new-request (:request new-context)]
    (is (= (:edn-params new-request) (java.sql.Timestamp. 0)))))

(deftest throws-an-error-if-eval-in-edn
  (is (thrown? Exception (i (as-context "application/edn" "#=(eval (println 1234)")))))

(deftest empty-body-does-nothing
  (let [empty-body  (as-context "application/edn" "")
        new-context (i empty-body)
        new-request (:request new-context)]
      (is (= (:edn-params new-request) nil))))

;; Translation: "Today is a good day to die."
(def klingon "Heghlu'meH QaQ jajvam")

(deftest unknown-content-type-does-nothing
  (let [unknown-content-type-context (as-context "application/klingon" klingon)
        new-context (i unknown-content-type-context)
        new-request (:request new-context)]
      (is (= (slurp (:body new-request)) klingon))))

(deftest nil-content-type-does-nothing
  (let [nil-content-type-context (as-context nil klingon)
        new-context (i nil-content-type-context)
        new-request (:request new-context)]
    (is (= (slurp (:body new-request)) klingon))))
