(ns tech.gdragon.commands.slash
  (:require [cl-java-introspector.core :refer [get-obj]])
  (:import (tech.gdragon.discord Bot)
           (tech.gdragon.commands.slash Info)
           (net.dv8tion.jda.api.entities Guild)
           (net.dv8tion.jda.api.sharding DefaultShardManager ShardManager)
           (net.dv8tion.jda.api.interactions.commands.build CommandData)
           (net.dv8tion.jda.api JDA)))

(def ^Bot bot (get-obj "bot"))

(def ^DefaultShardManager shard-manager (-> bot .api .getShardManager))

(defn find-guild
  "Returns the Guild with ID"
  [^DefaultShardManager shard-manager ^long guild-id]
  (.getGuildById shard-manager guild-id))

(defn retrieve-guild-commands
  "Get a list of commands for a Guild."
  [^Guild guild]
  (.. guild retrieveCommands complete))

(defn set-guild-command
  "Set Guild slash command."
  [^Guild guild ^CommandData command]
  (let [commands (into-array CommandData [command])]
    (.. guild updateCommands (addCommands commands) complete)))

(defn set-global-command
  "Set slash command **globally**."
  [^JDA jda ^CommandData command]
  (let [commands (into-array CommandData [command])]
    (.. jda updateCommands (addCommands commands) complete)))

(comment
  (require '[clojure.repl :refer [pst]])
  (pst *e)

  (set-global-command (.api bot) (.getCommand Info/INSTANCE))
  #_(let [guild (find-guild shard-manager )]
      ;(set-guild-command guild (.getCommand Info/INSTANCE))))
      (retrieve-guild-commands guild)))

(comment
  "Invite URL: https://discord.com/oauth2/authorize?client_id=338897906524225538&scope=applications.commands+bot&permissions=2251328512")
