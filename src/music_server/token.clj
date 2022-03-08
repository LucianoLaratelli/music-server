(ns music-server.token
  "Token refresh flow. I don't use private user info, so I don't need to do the OAuth2 way, but I did need auto-refreshing tokens."
  (:require
   [clj-http.client :as client]
   [clojure.core.async :refer [go-loop timeout <!]]
   [cprop.core :refer [load-config]]))

(def secrets (load-config :file "config.edn"))

(def client-id (atom (:client-id secrets)))
(def client-secret (atom (:client-secret secrets)))
(def token (atom nil))

(defn get-token
  []
  (-> "https://accounts.spotify.com/api/token"
      (client/post {:form-params {:grant_type "client_credentials"}
                    :basic-auth [@client-id @client-secret]
                    :as :json})))

(defn start-token-loop
  []
  (go-loop []
    (let [{tok :access_token expires :expires_in} (:body (get-token))]
      (reset! token tok)
      (println "reset token at" (str (new java.util.Date)))
      (println "will reset again in" (str expires))
      (<! (timeout (* 1000 expires)))
      (recur))))
