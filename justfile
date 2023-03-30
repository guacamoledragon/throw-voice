set shell := ["nu.exe", "-c"]
set dotenv-load := false

# Create uberjar for H2 restore script
package-h2-runscript:
  mvn -Pdev,h2-runscript package

# Create uberjar for H2 backup script
package-h2-script:
  mvn -Pdev,h2-script package

package-pawa-lite:
  mvn -Plite clean package

# Generate a backup of the Settings table on an instance of PostgresQL
pg-backup password='password' port='5432':
  docker run --rm -it --entrypoint= -e PGPASSWORD={{ password }} postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab /bin/sh -c \
  'pg_dump -h host.docker.internal -p {{ port }} -U postgres settings' \
  | save --raw $"(date now | date format "%Y-%m-%d")-settings.db"

# Apply Postgres DB migrations, expects password and optional port
pg-migrate password='password' port='5432':
  docker run --rm \
         -v ($env.PWD + "/sql:/flyway/sql") \
         -v ($env.PWD + "/conf:/flyway/conf") \
         -v ($env.PWD + "/data:/flyway/data") \
         flyway/flyway:8.5.12-alpine \
         -user=postgres -password={{ password }} \
         -url=jdbc:postgresql://host.docker.internal:{{ port }}/settings \
         migrate

# Expose Remote Postgres Database
pg-port-forward:
  ssh -L 5433:localhost:5432 -N -T pawa.im

# Restores a backup of the Settings table on an instance of PostgresQL
pg-restore backup password='password' port='5432':
  docker run --rm -it --entrypoint= -e PGPASSWORD={{ password }} -v {{ backup }}:/tmp/backup.db postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab bash -c \
  'psql -h host.docker.internal -p {{ port }} -U postgres settings < /tmp/backup.db'

# Start local PostgresQL instance for development
pg-start:
  docker run --rm --cpus 1.0 --name postgres -p 5432:5432 \
  -e POSTGRES_PASSWORD=password -e POSTGRES_DB=settings \
  -v pgdata:/var/lib/postgresql/data -v ($env.PWD + "/data/db-logs:/logs") \
  postgres:13

recover-mp3 id:
  scp pawa.im:/opt/pawa/data/recordings/{{ id }}.mp3 .

recover-queue id:
  scp pawa.im:/opt/pawa/data/recordings/{{ id }}.queue .
