(ns repl
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   (com.squareup.tape QueueFile QueueFile$ElementReader)
   (net.dv8tion.jda.api EmbedBuilder JDA)
   (net.dv8tion.jda.api.entities Activity Activity$ActivityType Guild)
   (net.dv8tion.jda.api.entities.channel.concrete TextChannel)
   (net.dv8tion.jda.api.sharding DefaultShardManager)
   (org.koin.java KoinJavaComponent)
   (tech.gdragon.discord Bot)
   (java.sql Connection Date DriverManager)
   (java.util Properties)))

(def ^Bot bot "bot" (delay (KoinJavaComponent/get Bot)))

(def ^DefaultShardManager shard-manager (delay (-> @bot .api .getShardManager)))

(defn create-pg-connection
  []
  (let [user     (System/getenv "DB_USER")
        password (System/getenv "DB_PASSWORD")
        host     (System/getenv "DB_HOST")
        db-name  (System/getenv "DB_NAME")
        url      (str "jdbc:postgresql://" host "/" db-name)
        props    (Properties.)]
    (.setProperty props "user" user)
    (.setProperty props "password" password)

    (DriverManager/getConnection url props)))

(def ^Connection conn (delay (create-pg-connection)))

(defn get-channel
  "Find Discord channel and return"
  [^JDA jda guild-name channel-name]
  (let [^Guild guild         (first (.getGuildsByName jda guild-name true))
        ^TextChannel channel (first (.getTextChannelsByName guild channel-name true))]
    channel))

(defn send-message!
  "Send a text message"
  [^TextChannel channel message]
  (.. channel (sendMessage message) (queue)))

(defn set-embed-fields!
  [^EmbedBuilder builder fields]
  (reduce
    (fn [^EmbedBuilder b field]
      (let [{:keys [title text inline?] :or {text "" inline? false}} field]
        (.addField b title text inline?)))
    builder
    fields))

(defn send-embed!
  "Send an embed message"
  [^TextChannel channel embed-map]
  (let [^EmbedBuilder builder (EmbedBuilder.)
        {:keys [description fields title]} embed-map
        message-embed         (cond-> builder
                                      title (.setTitle title)
                                      description (.setDescription description)
                                      fields (set-embed-fields! fields)
                                      true (.build))]
    (.. channel (sendMessage message-embed) (queue))))

(defn shard->field
  "Convert ShardInfo to field map."
  [^JDA shard]
  (let [status (.getStatus shard)
        emoji  (if (< 7 (.ordinal status)) ":x:" ":white_check_mark:")]
    {:title   (str emoji " " (.. shard getShardInfo getShardString))
     :text    (.toString status)
     :inline? true}))

(defn print-shard-status
  "docstring"
  [^DefaultShardManager shard-manager]
  (doseq [shard (.getShards shard-manager)]
    (println (.getShardInfo shard) (.getStatus shard) ": ")))

(comment
  (def channel (get-channel
                 (.api bot)
                 "Guacamole Dragon"
                 "bot-testing"))
  (send-message! channel "!status")
  (.queue (.leave (first (.getGuildsByName shard-manager "Kiinan Työsuojeluviranomainen [https://discord.gg/RWgNRj7]" true))))

  (let [fields (mapv shard->field (reverse (.getShards shard-manager)))]
    (send-embed! channel {:title       ":fleur_de_lis: Bot Status"
                          :description "Summary of @pawa's status."
                          :fields      fields})))

(defn queue->mp3
  "Create MP3 from Queue file"
  [queue-filename]
  (with-open [os (io/output-stream (io/file (str/replace queue-filename "queue" "mp3")))]
    (let [queue-file (QueueFile. (io/file queue-filename))]
      (.forEach queue-file (reify QueueFile$ElementReader
                             (read [this is length]
                               (.transferTo is os)))))))

(defn set-activity
  "Set bot's activity"
  [^Bot bot activity]
  (let [activity (Activity/of
                   Activity$ActivityType/LISTENING
                   (str "2.11.1 | https://pawa.im")
                   activity)]
    (.. bot api getPresence (setActivity activity))))

(comment
  (let [jda           (.api @bot)
        guild         (first (.getGuildsByName jda "Guacamole Dragon" true))
        audio-manager (.getAudioManager guild)]
    (.. audio-manager getConnectedChannel getName)))

(comment
  (import '(net.dv8tion.jda.api.sharding DefaultShardManager))

  (defn which-shard
    "Find the shard ID for a given guild ID"
    [jda]
    (let [shard-manager (.getShardManager jda)]))

  (comment
    (print-shard-status shard-manager)

    (do (.start shard-manager 8)
        (.start shard-manager 7))
    (do (.start shard-manager 6)
        (.start shard-manager 5))
    (.start shard-manager 4)))

(comment
  (-> @bot
      .api
      .getSelfUser
      .getApplicationIdLong))

(defn leave-guilds
  [guild-ids whitelist]
  (let [shard-manager (.. @bot api getShardManager)]
    (doseq [guild-id guild-ids
            :let [guild (.. shard-manager (getGuildById guild-id))]
            :when (nil? (whitelist guild-id))]

      (if (nil? guild)
        (print ".")
        (do
          (println "Leaving guild" guild-id)
          (.. shard-manager (getGuildById guild-id) leave complete)
          (Thread/sleep 1000))))))

(defn try-times*
  "Executes thunk. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n thunk]
  (loop [n n]
    (if-let [result (try
                      [(thunk)]
                      (catch Exception e
                        (when (zero? n)
                          (throw e))))]
      (result 0)
      (recur (dec n)))))

(defmacro try-times
  "Executes body. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n & body]
  `(try-times* ~n (fn [] ~@body)))

(comment
  ;; Leave inactive guilds, from Discord bot
  (def inactive-guild-ids
    (-> "/app/data/inactive-guilds-dec.txt"
        slurp
        str/split-lines))
  (def whitelist-guilds
    #{"408795211901173762"
      "110373943822540800"
      "273518732733710337"
      "264445053596991498"
      "446425626988249089"})
  (println "Starting to leave inactive guilds")
  (try-times 3 (leave-guilds inactive-guild-ids whitelist-guilds))
  (+ 1 1)
  ,)

(comment
  (def active-inactive-guild-ids
    (-> "/app/data/active-inactive-guilds-dec.txt"
        slurp
        str/split-lines))
  ;; Start making SQL queries with connection
  (doseq [id   (rest active-inactive-guild-ids),
          :let [query "update guilds set active=false, last_active_on=? where id=?"
                psmt  (.prepareStatement @conn query)
                now   (Date. (.getTime (java.util.Date.)))]]
    (.setDate psmt 1 now)
    (.setLong psmt 2 (Long/parseLong id))
    (println (.execute psmt))
    (.close psmt)
    (Thread/sleep 500))

  ;; Date Format
  ;; 2023-12-03 11:40:10.783308-08

  )
