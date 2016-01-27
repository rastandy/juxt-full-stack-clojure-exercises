;; Copyright © 2016, JUXT LTD.

(ns edge.webserver
  (:require
   [aleph.http :as http]
   [bidi.ring :refer [make-handler redirect]]
   [hiccup.core :refer [html]]
   [clojure.core.async :refer [chan >!! mult tap close!]]
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle using]]
   [schema.core :as s]
   [cheshire.core :as json]
   [byte-streams :as b]
   [om.next.server :as om]
   [yada.yada :refer [yada resource]]))

(def URI "http://live-cdn.me-tail.net/cantor/api/wanda/garment-details/81e36a81-1921-4247-a8de-1ed7eb67840f?skus=nct_sandbox_dress_37_7cpxa7,nct_sandbox_trousers_2_5bahk2,nct_sandbox_jacket_4_btcjsf,nct_sandbox_coat_3_vxbetj,nct_sandbox_top_24_7k94td,nct_sandbox_skirt_15_04s5md,nct_sandbox_top_15_zwzenw")

(defn get-garments [uri]
  {:garments
   (let [response (deref (http/get uri))]
     (if (= (:status response) 200)
       (json/decode (b/to-string (:body response)) keyword)
       (throw (Exception. "Oh no!!!"))))})

(defn readf [env k params]
  (infof "Reading k is %s" k)
  (let [st @(:state env)]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value :not-found})))

(defn mutatef [env k params]
  (infof "MUTATE! %s" k)
  {})

(defn om-resource [parser app-state]
  (resource
   {:methods
    {:post
     {:consumes "application/transit+json"
      :produces "application/transit+json"
      :response (fn [ctx]
                  (infof "query is %s" (:body ctx))
                  (let [msg
                        (parser {:state app-state} (:body ctx))]
                    (infof "msg is %s" msg)
                    msg
                    ))}}}))

(defn create-api [parser app-state]
  ["/"                   
   [
    ["garments" (yada (get-garments URI))]
    ["api" (yada (om-resource parser app-state))]
    ["" (redirect "index.html")]
    ["favicon.ico" (yada nil)]
    ["" (yada (io/file "target/dev"))]
    ]])

(s/defrecord Webserver [port :- (s/pred integer? "must be a port number!!")
                        app-state
                        server
                        api]
  Lifecycle
  (start [component]
    (let [app-state (atom
                     {:garments/by-id
                      (let [response (deref (http/get URI))]
                        (if (= (:status response) 200)
                          (into {}
                                (for [garment (json/decode (b/to-string (:body response)) keyword)]
                                  [(:id garment) garment]))
                          (throw (Exception. "Oh no!!!"))))})
          api (create-api (om/parser {:read readf :mutate mutatef}) app-state)]
      (assoc component
             :app-state app-state
             :server (http/start-server (make-handler api) component)
             :api api)))
  
  (stop [component]
    (when-let [server (:server component)] (.close server))
    component))

(defn new-webserver []
  (using
   (map->Webserver {:port 3000})
   []))
