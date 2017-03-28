;; Copyright © 2016, 2017, JUXT LTD.

(ns user
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.test :refer [run-all-tests]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [clojure.core.async :as a :refer [>! <! >!! <!! chan buffer dropping-buffer sliding-buffer close! timeout alts! alts!! go-loop]]
   [edge.system :as system]
   [reloaded.repl :refer [system init start stop go reset reset-all]]
   [schema.core :as s]
   [yada.test :refer [response-for]]
   [datomic.api :as d]
   [manifold.bus :as bus]
   [manifold.stream :as ms]
   [clojure.tools.logging :as log]))

(defn new-dev-system
  "Create a development system"
  []
  (let [config (system/config :dev)]
    (system/configure-components
     (component/system-using
      (system/new-system-map config)
      (system/new-dependency-map))
     config)))

(reloaded.repl/set-init! new-dev-system)

(defn check
  "Check for component validation errors"
  []
  (let [errors
        (->> system
             (reduce-kv
              (fn [acc k v]
                (assoc acc k (s/check (type v) v)))
              {})
             (filter (comp some? second)))]

    (when (seq errors) (into {} errors))))

(defn test-all []
  (run-all-tests #"edge.*test$"))

(defn reset-and-test []
  (reset)
  (time (test-all)))

(defn cljs-repl
  "Start a ClojureScript REPL"
  []
  (eval
   '(do (in-ns 'boot.user)
        (start-repl))))

;; REPL Convenience helpers

(defn dev-config []
  (system/config :dev))

(defmethod aero.core/reader 'dice-roll [{:keys [profile] :as opts} tag value]
  (rand-int 10))

(d/create-database "datomic:mem://training")

(def conn (d/connect "datomic:mem://training"))

(d/transact conn [{:db/ident :chat/message
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one}])

(defn chat [message]
  (d/transact conn [{:chat/message message}]))

(defn all-chat-messages []
  (d/q '[:find [(pull ?e [:chat/message]) ...]
         :where [?e :chat/message]]
       (d/db conn)))

(defn all-datomic-relations []
 (d/q '[:find [(pull ?e [*]) ...]
        :where
        [?e :db/ident]]
      (d/db conn)))
