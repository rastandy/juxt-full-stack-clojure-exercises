(ns edge.aleph
  (:require
   [aleph.http :as http]
   [bidi.ring :refer [make-handler redirect]]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [Lifecycle using]]
   [hiccup.core :refer [html]]
   [edge.chat :refer [get-channel]]
   [clojure.core.async :refer [chan >!! mult tap close!]]
   [schema.core :as s]
   [yada.yada :refer [yada resource]]))

(defn create-api [ch]
  ["/"                   
   [
    ["" (redirect ::index)]

    ["channel" (yada
                (resource
                 {:methods
                  {:get
                   {:produces "text/html"
                    :response
                    (fn [ctx] (str "Channel contains:" (.buf (.buf ch))))}}}))]

    ["close" (fn [req] (close! ch)
               {:status 200 :body "Channel closed"})]

    ["firehose" (let [mlt (mult ch)]
                  (yada
                   (resource
                    {:methods
                     {:get
                      {:response
                       (fn [ctx]
                         (let [ch (chan 10)]
                           (tap mlt ch false)
                           ch))
                       :produces "text/event-stream"}}})))]
    
    ["chat.html" (yada
                   (resource
                    {:id ::index
                     :methods
                     {:post
                      {:produces "text/html"
                       :consumes #{"application/x-www-form-urlencoded"
                                   "multipart/form-data"}
                       :parameters {:form {:message s/Str
                                           :topic s/Str}}
                       :response (fn [ctx]
                                   (let [message (format "%s: %s"
                                                         (get-in ctx [:parameters :form :topic])
                                                         (get-in ctx [:parameters :form :message]))]
                                     (>!! ch message)
                                     (html [:div
                                            [:p
                                             (str "Thanks: " message)]
                                            [:p
                                             (str "Channel contains:" (.buf (.buf ch)))]]
                                           )
                                     ))}
                      :get
                      {:produces #{"text/html"
                                   "application/json"
                                   "application/edn"}
                       
                       :response
                       (html [:body
                              [:form {:method "POST"}
                               [:input {:type :text :name "message"}]
                               [:input {:type :submit}]
                               ]])}}}))]

    ["favicon.ico" (yada nil)]
    ["" (yada (io/file "target/dev"))]
    ]])

(s/defrecord AlephWebserver [port :- (s/pred integer? "must be a port number!!")
                             messages
                             server
                             api]
  Lifecycle
  (start [component]
    (let [api (create-api (get-channel messages))]
      (assoc component
             :server (http/start-server (make-handler api) component)
             :api api)))
  
  (stop [component]
    (when-let [server (:server component)] (.close server))
    component))

(defn new-aleph-webserver []
  (using
   (map->AlephWebserver {:port 3000})
   [:messages]))
