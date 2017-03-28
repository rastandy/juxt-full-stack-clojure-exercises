(ns edge.chat
  (:require [yada.yada :as yada]
            [edge.events :as e]
            [manifold.stream :as ms]
            [clojure.tools.logging :as log]))

(defn chat-form [events]
  (yada/resource
   {:id :edge.resources/chat-form
    :description "A form to accept chat messages"
    :methods
    {:post {:parameters {:form {:message String}}
            :consumes #{"application/x-www-form-urlencoded"}
            :response (fn [ctx]
                        (e/publish events (get-in ctx [:parameters :form :message]) :chat)
                        "done")}}}))

(defn chat-messages [events]
  (yada/resource
   {:methods
    {:get {:produces #{"text/event-stream"}
           :response (fn [ctx]
                       (->> (edge.events/subscribe events)
                            (ms/map #(format "data: %s\n\n" (pr-str %)))))}}}))

(defn chat-routes [events]
  ["/chat"
   [["/form" (chat-form events)]
    ["/messages" (chat-messages events)]]])
