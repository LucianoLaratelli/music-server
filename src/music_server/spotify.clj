(ns music-server.spotify
  "Deals with calls to spotify and exposes wrappers for the server."
  (:require
   [clj-spotify.core :as spotify]
   [clojure.string :as s]
   [music-server.token :refer [token]]))

(defn process-album-tracks
  [album]
  (->> (get-in album [:tracks :items])
       (map #(select-keys % [:name :duration_ms :explicit]))
       (map #(assoc % :duration_s (/ (:duration_ms %) 1000.)))
       (map #(dissoc % :duration_ms))
       (assoc album :tracks)))

(defn pick-albums-from-duplicates
  "Multiple albums can come in for the same title. Prefer ones with explicit tracks. If no explicit tracks are on an album, choose the first one."
  [albums]
  (for [album albums
        :let [album-list (second album)
              first-album (first album-list)]]
    (if (= (count album-list) 1)
      first-album
      (let [albums-with-explicit-tracks (filter #(some :explicit (:tracks %)) album-list)]
        (if (seq albums-with-explicit-tracks)
          (first albums-with-explicit-tracks)
          first-album)))))

(defn albums-for-ids
  [album-ids]
  (when (seq album-ids)
    (into (:albums
           (spotify/get-several-albums
            {:ids (s/join "," (take 20 album-ids))
             :market "US"}
            @token))
          (albums-for-ids (drop 20 album-ids)))))

(defn trim-deluxe-albums
  [albums]
  (filter #(not (s/ends-with? (:name %) "(Deluxe)")) albums))

(defn album-ids-for-artist
  [id]
  (let [metadata (:items (spotify/get-an-artists-albums
                          {:id id
                           :include_groups "album"
                           :market "US"
                           :limit 50}
                          @token))]
    (->> metadata
         trim-deluxe-albums
         (map :id))))

(defn process-artists
  [album]
  (assoc album :artists (map :name (:artists album))))

(defn get-albums-for-artist-id
  "Given an artist id (an alphanumeric string) return a map of all the artist's albums on spotify."
  [id]
  (->> id
       album-ids-for-artist
       albums-for-ids
       (group-by :name)
       pick-albums-from-duplicates
       (map #(select-keys % [:name
                             :total_tracks
                             :date
                             :release_date
                             :artists
                             :images
                             :tracks]))
       (map process-album-tracks)
       (map process-artists)
       (map #(assoc (dissoc % :images) :image (:url (last (:images %)))))
       (map #(assoc (dissoc % :tracks) :tracks (map (fn [track] (select-keys track [:name :duration_s])) (:tracks %))))
       (sort-by :release_date)))

(defn get-album-data
  [{id :id :as data}]
  (assoc data :albums (get-albums-for-artist-id id)))

(defn get-artists-matching-name
  [name]
  (->> (sort-by :popularity >
                (map #(select-keys % [:name :id :popularity :external_urls :images])
                     (filter #(> (:popularity %) 0)
                             (get-in
                              (spotify/search {:q name
                                               :type "artist"}
                                              @token)
                              [:artists :items]))))
       (map #(assoc (dissoc % :images) :image (:url (last (:images %)))))
       (map #(assoc (dissoc % :external_urls) :URL (get-in % [:external_urls :spotify])))))
