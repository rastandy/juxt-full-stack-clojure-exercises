;; Copyright © 2016, 2017, JUXT LTD.

(ns edge.web-server
  (:require
   [bidi.bidi :refer [tag]]
   [bidi.vhosts :refer [make-handler vhosts-model]]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle using]]
   [clojure.java.io :as io]
   [edge.sources :refer [source-routes]]
   [hiccup.core :refer [html]]
   [edge.phonebook :refer [phonebook-routes]]
   [edge.phonebook-app :refer [phonebook-app-routes]]
   [edge.hello :refer [hello-routes other-hello-routes]]
   [edge.chat :refer [chat-routes]]
   [edge.security-demo :refer [security-demo-routes]]
   [schema.core :as s]
   [selmer.parser :as selmer]
   [yada.resources.webjar-resource :refer [new-webjar-resource]]
   [yada.yada :refer [handler resource] :as yada]))

(defn content-routes []
  ["/"
   [
    ["index.html"
     (yada/resource
      {:id :edge.resources/index
       :methods
       {:get
        {:produces #{"text/html"}
         :response (fn [ctx]
                     (selmer/render-file "index.html" {:title "Edge Index"
                                                       :ctx ctx}))}}})]

    ["" (assoc (yada/redirect :edge.resources/index) :id :edge.resources/content)]

    ;; Add some pairs (as vectors) here. First item is the path, second is the handler.
    ;; Here's an example

    [""
     (-> (yada/as-resource (io/file "target"))
         (assoc :id :edge.resources/static))]]])

(defn routes
  "Create the URI route structure for our application."
  [db config]
  [""
   [
    ;; Hello World!
    (hello-routes)
    ;; ["/chat" (yada/as-resource {:fruit "apple"})]
    (other-hello-routes)
    (chat-routes (:events config))
    (phonebook-routes db config)
    (phonebook-app-routes db config)

    (security-demo-routes)

    ["/api" (-> (hello-routes)
                ;; Wrap this route structure in a Swagger
                ;; wrapper. This introspects the data model and
                ;; provides a swagger.json file, used by Swagger UI
                ;; and other tools.
                (yada/swaggered
                 {:info {:title "Hello World!"
                         :version "1.0"
                         :description "An API on the classic example"}
                  :basePath "/api"})
                ;; Tag it so we can create an href to this API
                (tag :edge.resources/api))]

    ;; Swagger UI
    ["/swagger" (-> (new-webjar-resource "/swagger-ui" {:index-files ["index.html"]})
                    ;; Tag it so we can create an href to the Swagger UI
                    (tag :edge.resources/swagger))]

    ["/status" (yada/resource
                {:methods
                 {:get
                  {:produces "text/html"
                   :response (fn [ctx]
                               (html
                                [:body
                                 [:div
                                  [:h2 "System properties"]
                                  [:table
                                   (for [[k v] (sort (into {} (System/getProperties)))]
                                     [:tr
                                      [:td [:pre k]]
                                      [:td [:pre v]]]
                                     )]]
                                 [:div
                                  [:h2 "Environment variables"]
                                  [:table
                                   (for [[k v] (sort (into {} (System/getenv)))]
                                     [:tr
                                      [:td [:pre k]]
                                      [:td [:pre v]]]
                                     )]]
                                 ]))}}})]

    ;; The Edge source code is served for convenience
    (source-routes)

    ;; Our content routes, and potentially other routes.
    (content-routes)

    ;; This is a backstop. Always produce a 404 if we ge there. This
    ;; ensures we never pass nil back to Aleph.
    [true (handler nil)]]])

(s/defrecord WebServer [host :- s/Str
                        port :- s/Int
                        db
                        listener]
  Lifecycle
  (start [component]
    (if listener
      component ; idempotence
      (let [vhosts-model (vhosts-model [{:scheme :http :host host}
                                        (routes db {:port port
                                                    :events (:events component)})])
            listener (yada/listener vhosts-model {:port port})]
        (infof "Started web-server on port %s" (:port listener))
        (assoc component :listener listener))))

  (stop [component]
    (when-let [close (get-in component [:listener :close])]
      (close))
    (assoc component :listener nil)))

(defn new-web-server []
  (using
   (map->WebServer {})
   [:db]))
