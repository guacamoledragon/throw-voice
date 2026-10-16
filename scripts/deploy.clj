#!/usr/bin/env bb

;; Restart the bot only when no recording is active. See docs/specs/deploy-script.md.

(require '[babashka.cli :as cli]
         '[babashka.process :as p]
         '[bencode.core :as bencode]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(import '(java.net Socket)
        '(java.io PushbackInputStream)
        '(java.time Duration Instant LocalTime ZoneOffset ZonedDateTime))

(def cli-spec
  {:version      {:desc "Image tag to deploy" :require true}
   :window-start {:desc "Start of the daily window, UTC" :default "07:00"}
   :window-end   {:desc "End of the daily window, UTC" :default "10:00"}
   :max-days     {:desc "Stop with exit code 2 after this many windows with no deploy" :default 3 :coerce :long}
   :poll         {:desc "Seconds between two counts" :default 30 :coerce :long}
   :nrepl        {:desc "nREPL address" :default "127.0.0.1:7888"}
   :now          {:desc "Emergency: skip the window, cut recordings after --deadline" :coerce :boolean}
   :deadline     {:desc "With --now: minutes to wait for zero before the restart" :default 30 :coerce :long}
   :dry-run      {:desc "Do not change .env and do not restart" :coerce :boolean}})

(def cli-order [:version :window-start :window-end :max-days :poll :nrepl :now :deadline :dry-run])

(def opts nil)
(def recheck-ms 5000)
(def nrepl-down-limit (Duration/ofMinutes 10))
(def health-limit (Duration/ofMinutes 5))

(defn fail! [exit msg & {:as data}]
  (throw (ex-info msg (assoc data ::exit exit))))

(defn now [] (ZonedDateTime/now ZoneOffset/UTC))

(defn say [& xs] (println (str (Instant/now)) (str/join " " xs)))

(defn log!
  "Print one result and append it to deploy.log."
  [m]
  (let [line (pr-str (assoc m :at (str (Instant/now)) :version (:version opts)))]
    (say line)
    (when-not (:dry-run opts)
      (spit "deploy.log" (str line "\n") :append true))))

(defn sleep-until [^ZonedDateTime t]
  (let [ms (.toMillis (Duration/between (now) t))]
    (when (pos? ms) (Thread/sleep ms))))

;; nREPL

(defn- ->str [x] (if (bytes? x) (String. ^bytes x "UTF-8") x))

(defn nrepl-eval
  "Evaluate `code` on a new connection. Return the last value as a string."
  [code]
  (let [[host port] (str/split (:nrepl opts) #":")]
    (with-open [s (Socket. ^String host (int (parse-long port)))]
      (.setSoTimeout s 30000)
      (let [in (PushbackInputStream. (.getInputStream s))]
        (bencode/write-bencode (.getOutputStream s) {"op" "eval" "code" code})
        (loop [value nil]
          (let [msg    (update-vals (bencode/read-bencode in) #(if (sequential? %) (mapv ->str %) (->str %)))
                value  (or (get msg "value") value)
                status (set (get msg "status"))]
            (when-let [err (get msg "err")] (binding [*out* *err*] (print err)))
            (cond
              (get msg "ex") (fail! 1 (str "nREPL eval failed: " code) :ex (get msg "ex"))
              (status "done") value
              :else (recur value))))))))

(def down-since (atom nil))

(defn repl!
  "Evaluate a form in the bot. While the bot is not reachable, try again each --poll seconds."
  [form]
  (let [code (pr-str form)]
    (loop []
      (let [result (try
                     (nrepl-eval code)
                     (catch java.io.IOException e
                       (swap! down-since #(or % (now)))
                       (when (neg? (compare nrepl-down-limit (Duration/between @down-since (now))))
                         (fail! 1 "nREPL is not reachable" :since (str @down-since)))
                       (say "nREPL is not reachable:" (ex-message e))
                       ::down))]
        (if (= ::down result)
          (do (Thread/sleep (* 1000 (:poll opts))) (recur))
          (do (reset! down-since nil) (edn/read-string result)))))))

;; The jar has no Clojure namespaces, so these forms use the bot classes directly.
(def voice-guilds
  '(filter (fn [g] (.isConnected (.getAudioManager g)))
           (.getGuilds (.getShardManager (.api (org.koin.java.KoinJavaComponent/get tech.gdragon.discord.Bot))))))

(defn maintenance-form [on?]
  (list '.setMaintenance '(org.koin.java.KoinJavaComponent/get tech.gdragon.api.pawa.Pawa) (boolean on?)))

(defn active-count [] (repl! (list 'count voice-guilds)))

(defn active-sessions []
  (repl! (list 'mapv
               '(fn [g]
                  (let [h (.getReceivingHandler (.getAudioManager g))]
                    {:guild   (.getName g)
                     :session (when (instance? tech.gdragon.listener.AudioRecorder h) (.getSession h))}))
               voice-guilds)))

(def maintenance-on (atom false))

(defn maintenance! [on?]
  (repl! (maintenance-form on?))
  (reset! maintenance-on on?)
  (say "maintenance" (if on? "on" "off")))

;; Docker

(defn sh! [env & args]
  (let [r (try @(apply p/process {:extra-env env :out :string :err :string} args)
               (catch java.io.IOException e (fail! 1 (ex-message e))))]
    (when-not (zero? (:exit r))
      (fail! 1 (str (str/join " " args) " failed") :err (:err r)))
    (:out r)))

(defn pull! []
  (if (:dry-run opts)
    (say "dry run: no pull")
    (do (say "pull" (:version opts))
        (sh! {"VERSION" (:version opts)} "docker" "compose" "--compatibility" "pull" "bot"))))

(defn set-version!
  "Write VERSION in .env. Return the old value."
  [version]
  (let [env (slurp ".env")
        [_ previous] (re-find #"(?m)^VERSION=(.*)$" env)]
    (when-not previous (fail! 1 "No VERSION= line in .env"))
    (spit ".env" (str/replace env #"(?m)^VERSION=.*$" (str "VERSION=" version)))
    previous))

(defn healthy? [since]
  (str/includes? (sh! {} "docker" "compose" "logs" "--since" (str since) "bot") "Invite URL:"))

(defn restart!
  "Start the new version. Return the old version."
  []
  (if (:dry-run opts)
    (do (say "dry run: restart now")
        (maintenance! false)
        nil)
    (let [previous (set-version! (:version opts))
          since    (Instant/now)
          limit    (.plus (now) health-limit)]
      (say "restart:" previous "->" (:version opts))
      (sh! {} "docker" "compose" "--compatibility" "up" "-d" "bot")
      (reset! maintenance-on false)
      (loop []
        (cond
          (healthy? since) previous
          (.isAfter (now) limit) (fail! 1 "No \"Invite URL:\" in the bot log after 5 min" :previous previous)
          :else (do (Thread/sleep 5000) (recur)))))))

;; Rules

(defn next-window
  "The window that has not ended yet at `t`: [start end]. Start is `t` when `t` is in the window."
  [^ZonedDateTime t]
  (let [at    (fn [^ZonedDateTime day hhmm] (-> day (.with (LocalTime/parse hhmm)) (.withNano 0)))
        start (at t (:window-start opts))
        end   (at t (:window-end opts))
        [start end] (if (.isAfter t end) [(.plusDays start 1) (.plusDays end 1)] [start end])]
    [(if (.isAfter t start) t start) end]))

(defn wait-for-zero
  "Count until no recording is active and maintenance is on. Return true, or false when `end` comes first."
  [^ZonedDateTime end]
  (loop []
    (cond
      (.isAfter (now) end) false
      (pos? (active-count)) (do (Thread/sleep (* 1000 (:poll opts))) (recur))
      :else (do (maintenance! true)
                (Thread/sleep recheck-ms)
                (if (zero? (active-count))
                  true
                  (do (maintenance! false)
                      (Thread/sleep (* 1000 (:poll opts)))
                      (recur)))))))

(defn deploy-quiet! []
  (pull!)
  (let [started (now)]
    (loop [day 1]
      (let [[start end] (next-window (now))]
        (say "window" (str start) "to" (str end))
        (sleep-until start)
        (if (wait-for-zero end)
          (let [previous (restart!)]
            (log! {:result :deployed :previous previous
                   :waited-min (.toMinutes (Duration/between started (now)))}))
          (do (log! {:result :no-quiet-moment :day day})
              (when (>= day (:max-days opts))
                (fail! 2 (str "No quiet moment in " day " windows")))
              (recur (inc day))))))))

(defn deploy-now! []
  (pull!)
  (maintenance! true)
  (let [deadline (.plusMinutes (now) (:deadline opts))]
    (while (and (pos? (active-count)) (.isBefore (now) deadline))
      (Thread/sleep (* 1000 (:poll opts)))))
  (let [cut      (active-sessions)
        previous (restart!)]
    (log! {:result :deployed-now :previous previous :cut cut})))

(defn -main [args]
  (alter-var-root #'opts (constantly (cli/parse-opts args {:spec     cli-spec
                                                           :error-fn (fn [{:keys [msg]}]
                                                                       (println msg)
                                                                       (println (cli/format-opts {:spec cli-spec :order cli-order}))
                                                                       (System/exit 1))})))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(when @maintenance-on
                                (say "stopped with maintenance on, set it off")
                                (try (nrepl-eval (pr-str (maintenance-form false)))
                                     (catch Exception e (say "could not set maintenance off:" (ex-message e)))))))
  (try
    (if (:now opts) (deploy-now!) (deploy-quiet!))
    (catch clojure.lang.ExceptionInfo e
      (let [{::keys [exit] :as data} (ex-data e)]
        (when-not exit (throw e))
        (log! (merge {:result :error :message (ex-message e)} (dissoc data ::exit)))
        (System/exit exit)))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main *command-line-args*))
