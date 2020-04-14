#!/usr/bin/env spire
(require
  '[clojure.string :as str]
  '[clojure.edn :as edn])

(defn install-docker
  "Install Docker
   Source: https://docs.docker.com/engine/install/ubuntu/"
  [system]
  (let [packages ["apt-transport-https"
                  "ca-certificates"
                  "curl"
                  "gnupg-agent"
                  "software-properties-common"]]
    (apt :install packages)
    (shell {:cmd "curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -"})
    (shell {:cmd "lsb_release -cs"})
    (apt-repo :present {:repo (str "deb [arch=amd64] https://download.docker.com/linux/ubuntu " (name (:codename system)) " stable")
                        :filename "docker"})
    (apt :update)
    (apt :install ["docker-ce" "docker-ce-cli" "containerd.io"])))

(defn install-docker-compose
  "Install Docker Compose"
  [uname]
  (let [{:keys [os platform]} uname]
    (shell {:cmd     (str "curl -L https://github.com/docker/compose/releases/download/1.25.4/docker-compose-" os "-" platform " -o docker-compose")
            :dir     "/usr/local/bin/"
            :creates ["/usr/local/bin/docker-compose"]})
    (shell {:cmd "chmod +x docker-compose"
            :dir "/usr/local/bin/"})))

(defn install-local-persist
  "TODO: Install Docker Persist for Volumes"
  []
  (shell {:cmd "curl -fsSL https://raw.githubusercontent.com/MatchbookLab/local-persist/master/scripts/install.sh | sudo bash"}))

(defn install-tmux
  "Install TMUX
   Source: http://witkowskibartosz.com/blog/update-your-tmux-to-latest-version.html"
  []
  (let [build-packages ["libevent-dev" "libncurses-dev"]
        tmux-version "3.0a"
        tmux-tarball (str "tmux-" tmux-version ".tar.gz")]
       (apt :install build-packages)
       (shell {:cmd     (str/join tmux-version ["curl -L https://github.com/tmux/tmux/releases/download/" "/tmux-" ".tar.gz -o tmux-" ".tar.gz"])
               :dir     "/tmp"
               :creates [tmux-tarball]})
       (let [build-directory (str "/tmp/tmux-" tmux-version)]
            (shell {:cmd     (str "tar xf " tmux-tarball)
                    :dir     "/tmp"
                    :creates [build-directory]})
            (shell {:cmd "./configure"
                    :dir build-directory})
            (shell {:cmd "make"
                    :dir build-directory})
            (shell {:cmd "make install"
                    :dir build-directory
                    :creates ["/usr/local/bin/tmux"]}))))

(defn install-tpm
  "Install TPM"
  []
  (shell {:cmd "git clone https://github.com/tmux-plugins/tpm ~/.tmux/plugins/tpm"
          :creates ["~/.tmux/plugins/tpm"]})
  (upload {:src "./conf/tmux.conf"
           :dest "$HOME/.tmux.conf"
           :mode 0755}))

(defn install-restic
  "Install Restic
   Source: https://restic.readthedocs.io/en/stable/020_installation.html#official-binaries"
  [system]
  (let [{:keys [os platform]} system
        platform (case platform
                   :x86_64 "amd64")
        version "0.9.6"
        working-dir "/tmp"]
    (shell {:cmd (str "curl -L -o restic.bz2 https://github.com/restic/restic/releases/download/v" version "/restic_" version "_" (name os) "_" platform ".bz2")
            :dir working-dir
            :creates ["restic.bz2"]})
    (shell {:cmd "bzip2 -d restic.bz2"
            :dir working-dir
            :creates ["restic"]})
    (shell {:cmd "chmod +x restic"
            :dir working-dir})
    (shell {:cmd "mv restic /usr/local/bin/restic"
            :dir working-dir
            :creates ["/usr/local/bin/restic"]})
    (shell {:cmd "/usr/local/bin/restic self-update"})))

(defn configure-restic-backups
  "TODO: Configure automatic backups"
  []
  (shell {:cmd "mkdir -p /etc/restic"})

  ;; Create environment files
  (upload {:src "./restic/b2_env.sh"
           :dest "/etc/restic/b2_env.sh"
           :mode 0755})
  #_(upload {:src "./restic/restic_backup.sh"
             :dest "/usr/local/sbin/restic_backup.sh"
             :mode 0755})
  #_(upload {:src "./restic/restic_check.sh"
             :dest "/usr/local/sbin/restic_check.sh"
             :mode 0755})
  (upload {:src "./restic/b2_pw.txt"
           :dest "/etc/restic/b2_pw.txt"
           :mode 0600}))

(defn install-neovim
  "Install NeoVim"
  []
  (let [version "0.4.3"]
    (shell {:cmd (str "curl -L -o nvim https://github.com/neovim/neovim/releases/download/v" version "/nvim.appimage")
            :dir "/usr/local/bin"
            :creates ["/usr/local/bin/nvim"]})
    (shell {:cmd "chmod +x nvim"
            :dir "/usr/local/bin"})
    (comment
      "TODO: Install vim-plug"
      "TODO: Upload vimrc")))

(defn create-directory-structure
  "Creates the expected directory structure"
  []
  (let [base-dir "/opt/pawa/"]
    (doseq [dir ["data/database" "data/metrics" "data/recordings" "data/dump" "conf" "logs" "conf" "scripts" "sql"]]
      (shell {:cmd     (str "mkdir -p " base-dir dir)
              :creates (str base-dir dir)}))))

(defn copy-database-scripts
  "Copy migration database scripts"
  []
  (upload {:src "./sql"
           :dest "/opt/pawa/"
           :recurse true
           :mode 0755})
  (upload {:src "./conf/flyway.conf"
           :dest "/opt/pawa/conf/flyway.conf"
           :mode 0755})
  (upload {:src "./scripts/migrate.sh"
           :dest "/opt/pawa/scripts/migrate.sh"
           :mode 0755})
  (upload {:src "./scripts/info.sh"
           :dest "/opt/pawa/scripts/info.sh"
           :mode 0755}))

(defn copy-nginx-conf
  ""
  []
  (upload {:src "./conf/nginx.conf"
           :dest "/opt/pawa/nginx.conf"
           :mode 0755}))

(defn copy-docker-compose
  ""
  []
  (upload {:src "./docker-compose.yml"
           :dest "/opt/pawa/docker-compose.yml"
           :mode 0755}))

(defn copy-secrets
  ""
  []
  (upload {:src "./conf/prod.env"
           :dest "/opt/pawa/prod.env"
           :mode 0755}))

(if-let [target (second *command-line-args*)]
  (ssh target
    (let [facts    (get-fact)
          packages ["build-essential" "mosh" "tree"]]
      (apt :update)
      (apt :install packages)
      (install-tmux)
      (install-tpm)
      (install-restic (:system facts))
      (configure-restic-backups)
      (install-docker (:system facts))
      (install-docker-compose (:uname facts))
      (install-local-persist)
      (install-neovim)
      (create-directory-structure)
      (copy-database-scripts)
      (copy-nginx-conf)
      (copy-docker-compose)
      (copy-secrets))))
