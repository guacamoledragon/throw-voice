#!/usr/bin/env bb

(require '[babashka.process :as p]
         '[clojure.string :as str]
         '[clojure.java.io :as io]
         '[clojure.edn :as edn]
         '[cheshire.core :as json])

(def progress-file ".dep-progress")
(def repo "guacamoledragon/throw-voice")

;; --- Progress file helpers (EDN map: {pr-number status-keyword}) ---

(defn read-progress []
  (if (.exists (io/file progress-file))
    (edn/read-string (slurp progress-file))
    {}))

(defn write-progress! [m]
  (spit progress-file (pr-str m)))

(defn update-progress! [pr-number status]
  (write-progress! (assoc (read-progress) pr-number status)))

(defn blocked-entry
  "Returns [pr-number status] if there's a failed or in-progress entry, else nil."
  []
  (first (filter (fn [[_ s]] (#{:in-progress :failed} s)) (read-progress))))

;; --- Shell helpers ---

(defn sh
  "Run a command, return trimmed stdout. Throws on non-zero exit."
  [& args]
  (-> (apply p/process {:out :string :err :inherit} args)
      deref
      p/check
      :out
      str/trim))

;; --- Cherry-pick + test logic ---

(defn cherry-pick-and-test!
  "Cherry-pick a dependabot PR and run tests. Returns :passed or :failed.
   Exits the process on conflict."
  [pr-number branch-name pr-title]
  (println (str "\n=== PR #" pr-number ": " pr-title " (" branch-name ") ==="))
  (sh "git" "fetch" "github" branch-name)
  (let [cp (-> (p/process {:out :string :err :string} "git" "cherry-pick" "FETCH_HEAD") deref)]
    (if-not (zero? (:exit cp))
      (let [output (str (:out cp) (:err cp))]
        (if (str/includes? output "empty")
          (do
            (try (sh "git" "cherry-pick" "--abort") (catch Exception _))
            (update-progress! pr-number :passed)
            (println (str "PR #" pr-number " already applied, skipping."))
            :passed)
          (do
            (update-progress! pr-number :in-progress)
            (println (str "Cherry-pick conflict for PR #" pr-number ".\n" output "\n"
                          "Resolve conflicts and run 'git cherry-pick --continue', or run 'just dep-skip'."))
            (System/exit 1))))
      (do
        (update-progress! pr-number :in-progress)
        (println "Running tests...")
        (if (zero? (:exit (deref (p/process {:out :inherit :err :inherit} "mvn" "clean" "test"))))
          (do
            (update-progress! pr-number :passed)
            (println (str "PR #" pr-number " passed."))
            :passed)
          (do
            (update-progress! pr-number :failed)
            (println (str "PR #" pr-number " FAILED. Run 'just dep-skip' to undo."))
            :failed))))))

;; --- Subcommands ---

(defn cmd-skip []
  (let [[pr-number _] (blocked-entry)]
    (when-not pr-number
      (println "No in-progress or failed PR to skip.")
      (System/exit 1))

    (if (.exists (io/file ".git/CHERRY_PICK_HEAD"))
      (sh "git" "cherry-pick" "--abort")
      (sh "git" "reset" "--hard" "HEAD~1"))

    (update-progress! pr-number :skipped)
    (println (str "Skipped PR #" pr-number ", ready for next PR."))))

(defn cmd-test [pr-number]
  (let [pr-json (sh "gh" "pr" "view" (str pr-number) "--repo" repo
                     "--json" "headRefName,title")
        pr-data (json/parse-string pr-json true)
        branch  (:headRefName pr-data)
        title   (:title pr-data)]
    (when (= :failed (cherry-pick-and-test! pr-number branch title))
      (System/exit 1))))

(defn cmd-test-all []
  ;; Fetch open dependabot PRs sorted by createdAt ascending
  (let [prs-json (sh "gh" "pr" "list" "--repo" repo
                      "--state" "open" "--author" "app/dependabot"
                      "--json" "number,title,headRefName,createdAt")
        prs      (->> (json/parse-string prs-json true)
                      (sort-by :createdAt))]

    (when (empty? prs)
      (println "No open dependabot PRs found.")
      (System/exit 0))

    (let [progress (read-progress)]
      (loop [remaining prs
             processed 0
             skipped   0]
        (if-let [{:keys [number headRefName title]} (first remaining)]
          ;; Skip PRs already in progress file
          (if (contains? progress number)
            (do
              (println (str "Skipping PR #" number " (" (name (get progress number)) "): " title))
              (recur (rest remaining) processed (inc skipped)))
            (let [result (cherry-pick-and-test! number headRefName title)]
              (if (= :failed result)
                (do
                  (println "\nRun 'just dep-skip' to undo, then 'just dep-test-all' to resume.")
                  (System/exit 1))
                (recur (rest remaining) (inc processed) skipped))))
          ;; All done
          (println (str "\n=== Done: " processed " processed, " skipped " skipped ===\n"
                        "Push and rm .dep-progress when ready.")))))))

;; --- Entry point ---

(let [[cmd & args] *command-line-args*]
  (case cmd
    "test-all" (cmd-test-all)
    "test"     (if-let [pr (first args)]
                 (cmd-test (parse-long pr))
                 (do (println "Usage: dep.clj test <pr_number>")
                     (System/exit 1)))
    "skip"     (cmd-skip)
    (do
      (println (str "Usage: dep.clj <test-all|test|skip>\n\n"
                    "  test-all           Process all open dependabot PRs\n"
                    "  test <pr_number>   Process a single PR by number\n"
                    "  skip               Undo the last failed/in-progress cherry-pick"))
      (System/exit (if cmd 1 0)))))
