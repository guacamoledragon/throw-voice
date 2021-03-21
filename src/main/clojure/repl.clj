(ns repl
  (:import (tech.gdragon.discord Bot)
           (net.dv8tion.jda.api JDA JDA$Status EmbedBuilder JDA$ShardInfo)
           (net.dv8tion.jda.api.entities Activity Activity$ActivityType Guild TextChannel)
           (net.dv8tion.jda.api.sharding DefaultShardManager)
           (com.squareup.tape QueueFile QueueFile$ElementReader))
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(use 'cl-java-introspector.core)

(def ^Bot bot (get-obj "bot"))

(def ^DefaultShardManager shard-manager (-> bot .api .getShardManager))

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
                   Activity$ActivityType/DEFAULT
                   (str "2.9.2 | https://pawa.im")
                   activity)]
    (.. bot api getPresence (setActivity activity))))

(comment
  (let [jda           (.api bot)
        guild         (first (.getGuildsByName jda "Guacamole Dragon" true))
        audio-manager (.getAudioManager guild)]
    (.. audio-manager getConnectedChannel getName)))

(comment
  (use 'cl-java-introspector.core)
  (import '(net.dv8tion.jda.api.sharding DefaultShardManager))
  (def bot (get-obj "bot"))

  (import '(org.koin.core.context GlobalContext))

  ;(.setProperty (.get (GlobalContext/INSTANCE)) "MAINTENANCE" "true")

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
