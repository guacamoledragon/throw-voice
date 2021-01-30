(use 'cl-java-introspector.core)
(import '(net.dv8tion.jda.api.sharding DefaultShardManager))
(def bot (get-obj "bot"))
(def ^DefaultShardManager shard-manager (-> bot .api .getShardManager))

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
  ())

(comment
  (doseq [shard (.getShards shard-manager)]
    (print (.getShardInfo shard) (.getStatus shard) ": ")
    (println (.getGuildById shard 408795211901173762)))

  (.restart shard-manager))
