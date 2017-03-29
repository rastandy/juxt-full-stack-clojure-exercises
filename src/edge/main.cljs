;; Copyright © 2016, 2017, JUXT LTD.

(ns edge.main
  (:require
   [reagent.core :as r]
   [edge.chat-app :as chat]))

(defn init []
  (enable-console-print!)

  (when-let [section (. js/document (getElementById "chat"))]
    (println "Chat")
    (chat/init section))

  (println "Congratulations - your environment seems to be working"))
