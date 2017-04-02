;; Copyright © 2016, 2017, JUXT LTD.

;; Here's a simple single page app written in ClojureScript that uses
;; Reagent.

(ns edge.chat-app
  (:require
   [reagent.core :as r]
   [cljs.reader :refer [read-string]]))

(def app-state (r/atom {:messages []}))

(defn get-chat-data []
  (println "Loading chat data")
  (doto
      (new js/XMLHttpRequest)
      (.open "GET" "/chat")
      (.setRequestHeader "Accept" "application/edn")
      (.addEventListener
       "load"
       (fn [evt]
         (swap! app-state
                assoc :messages
                (read-string evt.currentTarget.responseText))))
      (.send)))

(defn chat []
  (fn []
    (let [state @app-state]
      [:div
       [:p "Chat Messages"]
       [:table
        [:tbody
         (map-indexed (fn [idx message]
                        [:tr {:key idx} [:td message]])
                      (:messages state))]]])))

(defn init [section]
  ;; (get-chat-data)

  (println "Subscribing")
  (let [es (new js/EventSource "/chat")]
    (.addEventListener
     es "message"
     (fn [ev]
       (swap! app-state update :messages conj (.-data ev)))))

  (r/render-component [chat] section))
