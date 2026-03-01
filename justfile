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
