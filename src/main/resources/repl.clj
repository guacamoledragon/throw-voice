(ns repl
  (:import (tech.gdragon.discord Bot)
           (net.dv8tion.jda.api JDA)
           (net.dv8tion.jda.api.entities Guild TextChannel)
           (com.squareup.tape QueueFile QueueFile$ElementReader)
           (java.io InputStream))
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(use 'cl-java-introspector.core)

(def ^Bot bot (get-obj "bot"))

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

(def channel (get-channel
               (.api bot)
               "Guacamole Dragon"
               "bot-testing"))

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
