set dotenv-load := false

# Run the app locally with local S3 (Minio), equivalent to IntelliJ "App Dev (local-s3)"
run-dev:
  BOT_STANDALONE=false LOG_LEVEL=info OVERRIDE_FILE=dev.properties \
  mvn clean compile exec:java \
    -Dexec.mainClass=tech.gdragon.App \
    -Dlog4j.configurationFile=log4j2-prod.xml

docker-build:
  docker build \
    --cache-from registry.gitlab.com/pawabot/pawa:2.16.0 \
    -t pawa:dev \
    --build-arg BUILD_DATE=(date now | format date "%FT%TZ") \
    --build-arg VCS_REF=(git rev-parse --short @) \
    --build-arg VERSION=dev \
    .

docker-run:
  docker run --rm -it \
    --env BOT_STANDALONE=false --env OVERRIDE_FILE=settings.properties --env OTEL_JAVAAGENT_ENABLED=false --env TZ="America/Los_Angeles" \
    -v ($env.PWD + "/dev.docker.properties:/app/settings.properties") \
    -v ($env.PWD + "/data:/app/data") \
    -p 7888:7888 \
    pawa:dev

# Start local Minio instance for development
minio-start:
  docker run --rm -it --name minio -p 9090:9000 -p 9091:9091 \
  -e MINIO_ROOT_USER=minio -e MINIO_ROOT_PASSWORD=password -e MINIO_CONSOLE_ADDRESS=:9091 \
  minio/minio:RELEASE.2025-05-24T17-08-30Z \
  server /opt/data

package-pawalite:
  mvn --version
  mvn -Plite clean package

# Generate a backup of the Settings table on an instance of PostgresQL
pg-backup password='password' port='5432':
  docker run --rm -it --entrypoint= \
  -e PGPASSWORD={{ password }} \
  postgres:17.2-alpine /bin/sh -c \
  'pg_dump -h host.docker.internal -p {{ port }} -U postgres settings' \
  | save --raw $"(date now | format date "%Y-%m-%d")-settings.db"

# Apply Postgres DB migrations, expects password and optional port
pg-migrate password='password' port='5432':
  docker run --rm \
         -v ($env.PWD + "/sql:/flyway/sql") \
         -v ($env.PWD + "/conf:/flyway/conf") \
         -v ($env.PWD + "/data:/flyway/data") \
         flyway/flyway:10.11-alpine \
         -user=postgres -password={{ password }} \
         -url=jdbc:postgresql://host.docker.internal:{{ port }}/settings \
         migrate

# Expose Remote Postgres Database
pg-port-forward:
  ssh -L 5433:localhost:5432 -N -T pawa.im

# Restores a backup of the Settings table on an instance of PostgresQL
pg-restore backup password='password' port='5432':
  docker run --rm -it --entrypoint= \
  -e PGPASSWORD={{ password }} \
  -v {{ backup }}:/tmp/backup.db \ # Not working because of the path
  postgres:17.2-alpine bash -c \
  'psql -h host.docker.internal -p {{ port }} -U postgres settings < /tmp/backup.db'

# Start local PostgresQL instance for development
pg-start:
  docker run -it --rm --cpus 1.0 --name postgres -p 5432:5432 \
  -e POSTGRES_PASSWORD=password -e POSTGRES_DB=settings \
  -v pgdata:/var/lib/postgresql/data -v "${PWD}/data/db-logs:/logs" \
  postgres:17.2-alpine

recover-mp3 id:
  scp pawa.im:/opt/pawa/data/recordings/{{ id }}.mp3 .

recover-queue id:
  scp pawa.im:/opt/pawa/data/recordings/{{ id }}.queue .
  java -jar ($env.PWD + "/../pawalite/pawa-recovery-tool.jar") {{ id }}.queue

# Expose Clojure REPL
repl-port-forward:
  ssh -L 7888:localhost:7888 -N -T pawa.im

# Undo the last failed/in-progress dependabot cherry-pick and mark it as skipped
dep-skip:
  #!/usr/bin/env bash
  set -euo pipefail

  PROGRESS_FILE=".dep-progress"

  if [[ ! -f "$PROGRESS_FILE" ]]; then
    echo "No .dep-progress file found — nothing to skip."
    exit 1
  fi

  # Find the last in-progress or failed entry
  pr_line=$(grep -E ':(in-progress|failed)$' "$PROGRESS_FILE" | tail -1 || true)

  if [[ -z "$pr_line" ]]; then
    echo "No in-progress or failed PR to skip."
    exit 1
  fi

  pr_number="${pr_line%%:*}"

  # Check if a cherry-pick is in progress (conflicted state)
  if [[ -d ".git/CHERRY_PICK_HEAD" ]] || [[ -f ".git/CHERRY_PICK_HEAD" ]]; then
    echo "Cherry-pick in progress (conflict detected), aborting..."
    git cherry-pick --abort
  else
    # Verify HEAD looks like a dependabot commit
    head_msg=$(git log -1 --format='%s')
    if [[ ! "$head_msg" =~ ^Bump\ .+\ from\ .+\ to\ .+ ]]; then
      echo "HEAD doesn't look like a dependabot commit, refusing to reset."
      echo "HEAD message: $head_msg"
      exit 1
    fi
    echo "Resetting HEAD (undoing cherry-pick)..."
    git reset --hard HEAD~1
  fi

  # Update the entry in .dep-progress to skipped
  sed -i "s/^${pr_number}:.*$/${pr_number}:skipped/" "$PROGRESS_FILE"
  echo "Skipped PR #${pr_number}, ready for next PR."

# Process a single dependabot PR by number — cherry-pick, compile, and test
dep-test pr_number:
  #!/usr/bin/env bash
  set -euo pipefail

  PROGRESS_FILE=".dep-progress"
  REPO="guacamoledragon/throw-voice"
  PR_NUMBER="{{ pr_number }}"

  # 1. Warn if not on master
  current_branch=$(git branch --show-current)
  if [[ "$current_branch" != "master" ]]; then
    echo "Warning: not on master (currently on '$current_branch')."
    echo "Cherry-picks will land on this branch instead of master."
    echo ""
  fi

  # 2. Check for failed or in-progress entries
  if [[ -f "$PROGRESS_FILE" ]]; then
    blocked=$(grep -E ':(in-progress|failed)$' "$PROGRESS_FILE" || true)
    if [[ -n "$blocked" ]]; then
      echo "Error: there is a failed or in-progress PR that needs to be resolved first."
      echo "$blocked"
      echo "Run 'just dep-skip' to undo it before processing more PRs."
      exit 1
    fi
  fi

  # 3. Get branch name from PR
  branch_name=$(gh pr view "$PR_NUMBER" --repo "$REPO" --json headRefName --jq '.headRefName')
  pr_title=$(gh pr view "$PR_NUMBER" --repo "$REPO" --json title --jq '.title')
  echo "Processing PR #${PR_NUMBER}: ${pr_title}"
  echo "  Branch: ${branch_name}"

  # 4. Fetch and cherry-pick
  git fetch github "$branch_name"
  if ! git cherry-pick FETCH_HEAD; then
    echo ""
    echo "Cherry-pick failed (conflict) for PR #${PR_NUMBER}."
    echo "Options:"
    echo "  - Resolve conflicts, then: git cherry-pick --continue"
    echo "  - Or abort: just dep-skip"
    echo "${PR_NUMBER}:in-progress" >> "$PROGRESS_FILE"
    exit 1
  fi

  # 5. Record in-progress
  echo "${PR_NUMBER}:in-progress" >> "$PROGRESS_FILE"

  # 6. Run tests
  echo "Running tests..."
  if mvn clean test; then
    sed -i "s/^${PR_NUMBER}:in-progress$/${PR_NUMBER}:passed/" "$PROGRESS_FILE"
    echo ""
    echo "PR #${PR_NUMBER} passed: ${pr_title}"
  else
    sed -i "s/^${PR_NUMBER}:in-progress$/${PR_NUMBER}:failed/" "$PROGRESS_FILE"
    echo ""
    echo "PR #${PR_NUMBER} FAILED: ${pr_title}"
    echo "The cherry-picked commit is still on master for inspection."
    echo "Run 'just dep-skip' to undo before continuing."
    exit 1
  fi

# Process all open dependabot PRs sequentially — cherry-pick, compile, test each one; stop on first failure
dep-test-all:
  #!/usr/bin/env bash
  set -euo pipefail

  PROGRESS_FILE=".dep-progress"
  REPO="guacamoledragon/throw-voice"

  # 1. Warn if not on master
  current_branch=$(git branch --show-current)
  if [[ "$current_branch" != "master" ]]; then
    echo "Warning: not on master (currently on '$current_branch')."
    echo "Cherry-picks will land on this branch instead of master."
    echo ""
  fi

  # 2. Check for failed or in-progress entries
  if [[ -f "$PROGRESS_FILE" ]]; then
    blocked=$(grep -E ':(in-progress|failed)$' "$PROGRESS_FILE" || true)
    if [[ -n "$blocked" ]]; then
      echo "Error: there is a failed or in-progress PR that needs to be resolved first."
      echo "$blocked"
      echo "Run 'just dep-skip' to undo it before processing more PRs."
      exit 1
    fi
  fi

  # 3. Fetch the list of open dependabot PRs sorted by createdAt ascending
  prs=$(gh pr list --repo "$REPO" --state open --author "app/dependabot" \
    --json number,title,headRefName,createdAt \
    --jq 'sort_by(.createdAt) | .[] | [.number, .headRefName, .title] | @tsv')

  if [[ -z "$prs" ]]; then
    echo "No open dependabot PRs found."
    exit 0
  fi

  # 4. Process each PR
  total=0
  processed=0
  skipped_count=0

  while IFS=$'\t' read -r pr_number branch_name pr_title; do
    total=$((total + 1))

    # Skip PRs already recorded in .dep-progress
    if [[ -f "$PROGRESS_FILE" ]] && grep -q "^${pr_number}:" "$PROGRESS_FILE"; then
      status=$(grep "^${pr_number}:" "$PROGRESS_FILE" | tail -1 | cut -d: -f2)
      echo "Skipping PR #${pr_number} (${status}): ${pr_title}"
      skipped_count=$((skipped_count + 1))
      continue
    fi

    echo ""
    echo "=== Processing PR #${pr_number}: ${pr_title} ==="
    echo "  Branch: ${branch_name}"

    # Fetch and cherry-pick
    git fetch github "$branch_name"
    if ! git cherry-pick FETCH_HEAD; then
      echo ""
      echo "Cherry-pick failed (conflict) for PR #${pr_number}."
      echo "Options:"
      echo "  - Resolve conflicts, then: git cherry-pick --continue"
      echo "  - Or abort: just dep-skip"
      echo "${pr_number}:in-progress" >> "$PROGRESS_FILE"
      exit 1
    fi

    # Record in-progress
    echo "${pr_number}:in-progress" >> "$PROGRESS_FILE"

    # Run tests
    echo "Running tests..."
    if mvn clean test; then
      sed -i "s/^${pr_number}:in-progress$/${pr_number}:passed/" "$PROGRESS_FILE"
      echo ""
      echo "PR #${pr_number} passed: ${pr_title}"
      processed=$((processed + 1))
    else
      sed -i "s/^${pr_number}:in-progress$/${pr_number}:failed/" "$PROGRESS_FILE"
      echo ""
      echo "PR #${pr_number} FAILED: ${pr_title}"
      echo "The cherry-picked commit is still on master for inspection."
      echo "Run 'just dep-skip' to undo, then 'just dep-test-all' to resume."
      exit 1
    fi
  done <<< "$prs"

  echo ""
  echo "=== Summary ==="
  echo "  Processed: ${processed}"
  echo "  Skipped:   ${skipped_count}"
  echo "  Total:     ${total}"
  echo ""
  echo "All PRs processed successfully! Next steps:"
  echo "  git log --oneline origin/master..master   # Preview commits"
  echo "  git push origin master && git push github master"
  echo "  rm .dep-progress"
