(ns edge.chat
  (:require [yada.yada :as yada]
            [selmer.parser :as selmer]
            [edge.events :as e]
            [manifold.stream :as ms]
            [clojure.tools.logging :as log]))

(defn chat [events]
  (yada/resource
   {:id :edge.resources/chat-form
    :description "A form to accept chat messages"
    :methods
    {:post {:parameters {:form {:message String}}
            :consumes #{"application/x-www-form-urlencoded"}
            :response (fn [ctx]
                        (e/publish events :chat (get-in ctx [:parameters :form :message]))
                        "done")}
     :get {:produces #{"text/event-stream"}
           :response (fn [ctx]
                       (->> (e/subscribe events :chat)
                            (ms/map #(format "data: %s\n\n" (str %)))))}}}))

(defn chat-app []
  (yada/resource
   {:id :edge.resources/chat-app
    :methods
    {:get
     {:produces "text/html"
      :response
      (fn [ctx] (selmer/render-file "chat-app.html"
                                    {:title "Edge Chat app" :ctx ctx}))}}}))

(defn chat-routes [events]
  ["/" [["chat-app" (chat-app)]
        ["chat" (chat events)]]])
