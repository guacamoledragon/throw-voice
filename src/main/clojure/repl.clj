(ns repl
  (:import (tech.gdragon.discord Bot)
           (net.dv8tion.jda.api JDA)
           (net.dv8tion.jda.api.entities Guild TextChannel)
           (net.dv8tion.jda.api.sharding DefaultShardManager)
           (com.squareup.tape QueueFile QueueFile$ElementReader)
           (java.io InputStream))
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

(comment
  (def channel (get-channel
                 (.api bot)
                 "Guacamole Dragon"
                 "bot-testing"))
  (send-message! channel "!status"))

(comment (send-message! channel "Hello World!"))

(defn queue->mp3
  "Create MP3 from Queue file"
  [queue-filename]
  (with-open [os (io/output-stream (io/file (str/replace queue-filename "queue" "mp3")))]
    (let [queue-file (QueueFile. (io/file queue-filename))]
      (.forEach queue-file (reify QueueFile$ElementReader
                             (read [this is length]
                               (.transferTo is os)))))))

(comment
  (let [jda           (.api bot)
        guild         (first (.getGuildsByName jda "Guacamole Dragon" true))
        audio-manager (.getAudioManager guild)]
    (.. audio-manager getConnectedChannel getName)))

(comment
  (use 'cl-java-introspector.core)
  (import '(net.dv8tion.jda.api.sharding DefaultShardManager))
  (def bot (get-obj "bot"))

  (import '(org.koin.core.context GlobalContext)
          '(net.dv8tion.jda.api.entities Activity)
          '(net.dv8tion.jda.api.entities Activity$ActivityType))

  ;(.setProperty (.get (GlobalContext/INSTANCE)) "MAINTENANCE" "true")

  (defn which-shard
    "Find the shard ID for a given guild ID"
    [jda]
    (let [shard-manager (.getShardManager jda)]))


  (def activity
    (Activity/of
      Activity$ActivityType/DEFAULT
      (str "2.9.1 | https://pawa.im")
      "https://pawa.im"))

  (comment
    ;; Set pawa's status
    (-> bot
        .api
        .getPresence
        (.setActivity activity)))

  (comment
    (require '[criterium.core :as crit])
    (import '(org.jetbrains.exposed.sql.transactions ThreadLocalTransactionManagerKt)
            '(kotlin.jvm.functions Function1)
            '(tech.gdragon BotUtils))
    (let [guild (.getGuildById shard-manager 333055724198559745)]
      (crit/with-progress-reporting
        (crit/bench
          (.getPrefix BotUtils/INSTANCE guild)))))

  (comment
    (doseq [shard (.getShards shard-manager)]
      (println (.getShardInfo shard) (.getStatus shard) ": "))

    (.restart shard-manager 8)))
