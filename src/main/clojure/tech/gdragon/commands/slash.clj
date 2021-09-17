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

(defn retrieve-global-commands
  "Get a list of global commands."
  [^JDA jda]
  (.. jda retrieveCommands complete))

(defn set-guild-command
  "Set Guild slash command."
  [^Guild guild ^CommandData command]
  (let [commands (into-array CommandData [command])]
    (.. guild updateCommands (addCommands commands) complete)))

(defn remove-guild-command
  "Remove Guild slash command"
  [^Guild guild ^String command-id]
  (.. guild (deleteCommandById command-id) complete))

(defn set-global-command
  "Set slash command **globally**."
  [^JDA jda ^CommandData command]
  (let [commands (into-array CommandData [command])]
    (.. jda updateCommands (addCommands commands) complete)))

(comment
  (require '[clojure.repl :refer [pst]])
  (pst *e)

  #_(let [guild (find-guild shard-manager 408795211901173762)
          jda   (.api bot)]
      (println
        (retrieve-guild-commands guild)
        (retrieve-global-commands jda)))

  ;(set-global-command (.api bot) (.getCommand Info/INSTANCE))
  #_(let [guild (find-guild shard-manager)]
      ;(set-guild-command guild (.getCommand Info/INSTANCE))))
      (retrieve-guild-commands guild)))

(comment
  "Invite URL: https://discord.com/oauth2/authorize?client_id=338897906524225538&scope=applications.commands+bot&permissions=2251328512")
