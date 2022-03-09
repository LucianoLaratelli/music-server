(ns music-server.core
  "Entry point and server routes."
  (:require
   [jsonista.core :as j]
   [music-server.spotify :refer [get-artists-matching-name get-albums-for-artist-id]]
   [music-server.token :refer [start-token-loop]]
   [muuntaja.core :as m]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.params :as params]))

;;TODO: what happens if we get a `next` key returned in any API call?

(def mapper
  (j/object-mapper
   {:decode-key-fn keyword}))

(def app
  (ring/ring-handler
   (ring/router
    [["/search-artists"
      {:get (fn [req]
              (let [name (get-in req [:query-params "artist_name"])]
                (if name
                  {:status 200
                   :body {:artists (get-artists-matching-name name)}}
                  {:status 404
                   :body "got empty name"})))}]
     ["/get-albums"
      {:get (fn [req]
              (let [artist (j/read-value (get-in req [:query-params "artist"]) mapper)]
                (if artist
                  {:status 200
                   :body {:artists (get-albums-for-artist-id artist)}}
                  {:status 404
                   :body "got no data"})))}]]
    {:data {:muuntaja m/instance
            :middleware [params/wrap-params
                         muuntaja/format-middleware
                         coercion/coerce-exceptions-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware]}})

   (ring/create-default-handler)))

(defonce server (do (jetty/run-jetty #'app {:port 3000, :join? false})
                    (start-token-loop)
                    (println "server running on 3000 at" (str (new java.util.Date)))))

(defn stop []
  (.stop server))
