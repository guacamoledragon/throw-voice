(ns repl
  (:import (tech.gdragon.discord Bot)
           (net.dv8tion.jda.api JDA)
           (net.dv8tion.jda.api.entities Guild TextChannel MessageChannel)))

(use 'cl-java-introspector.core)

(def ^Bot bot (get-obj "bot"))

(defn get-channel
  "Find Discord channel and return"
  [^JDA jda guild-name channel-name]
  (let [^Guild guild (first (.getGuildsByName jda guild-name true))
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

(send-message! channel "Hello World!")
