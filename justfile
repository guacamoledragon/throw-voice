set shell := ["nu.exe", "-c"]
set dotenv-load := false

docker-build:
  docker build \
    --cache-from registry.gitlab.com/pawabot/pawa:2.15.1 \
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
  | save --raw $"(date now | date format "%Y-%m-%d")-settings.db"

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
  -v pgdata:/var/lib/postgresql/data -v ($env.PWD + "/data/db-logs:/logs") \
  postgres:17.2-alpine

recover-mp3 id:
  scp pawa.im:/opt/pawa/data/recordings/{{ id }}.mp3 .

recover-queue id:
  scp pawa.im:/opt/pawa/data/recordings/{{ id }}.queue .
  java -jar ($env.PWD + "/../pawalite/pawa-recovery-tool.jar") {{ id }}.queue

# Expose Clojure REPL
repl-port-forward:
  ssh -L 7888:localhost:7888 -N -T pawa.im
