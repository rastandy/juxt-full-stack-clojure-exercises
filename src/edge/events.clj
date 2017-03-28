(ns edge.events
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [clojure.tools.logging :as log]
            [manifold.bus :as bus]
            [manifold.stream :as ms]))

(defrecord Events []
  Lifecycle
  (start [this]
    (log/info "Starting events")
    (assoc this
           :state :started
           :bus (bus/event-bus)))
  (stop [this]
    (log/info "Stoppint events")
    (assoc this
           :state :stopped
           :bus (when-let [bus (:bus this)]
                  (doseq [stream (bus/downstream bus :chat)]
                    (ms/close! stream))))))

(defn new-events []
  (map->Events {}))

(defn get-events-bus [events-component]
  (-> events-component :bus))

(defn chat-messages-stream [events-component]
  (bus/subscribe (get-events-bus events-component) :chat))

(defn publish [events-component event topic]
  (bus/publish! (get-events-bus events-component) topic event))

(defn subscribe [events]
  (bus/subscribe (get-events-bus events) :chat))
