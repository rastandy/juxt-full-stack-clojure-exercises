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
  (:bus events-component))

(defn publish [events-component topic event]
  (bus/publish! (get-events-bus events-component) topic event))

(defn subscribe [events topic]
  (bus/subscribe (get-events-bus events) topic))
