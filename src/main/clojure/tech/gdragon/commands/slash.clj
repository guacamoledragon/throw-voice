(ns tech.gdragon.commands.slash
  (:require [cl-java-introspector.core :refer [get-obj]])
  (:import (tech.gdragon.discord Bot)
           (tech.gdragon.commands.slash Info)
           (net.dv8tion.jda.api.entities Guild)
           (net.dv8tion.jda.api.sharding DefaultShardManager)
           (net.dv8tion.jda.api.interactions.commands.build CommandData)))

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

(comment
  (require '[clojure.repl :refer [pst]])
  (pst *e)
  (let [guild (find-guild shard-manager 333055724198559745)]
    #_(set-guild-command guild (.getCommand Info/INSTANCE))
    (retrieve-guild-commands guild)))
