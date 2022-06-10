set shell := ["nu.exe", "-c"]
set dotenv-load := false

package-pawa-lite:
  mvn -Plite package

# Create uberjar for H2 backup script
package-h2-script:
  mvn -Pdev,h2-script package

# Create uberjar for H2 restore script
package-h2-runscript:
  mvn -Pdev,h2-runscript package

# Expose Remote Postgres Database
postgres-pf:
  ssh -L 5433:localhost:5432 -N -T pawa.im

# Apply Postgres DB migrations, expects password and optional port
db-migrate password='password' port='5432':
  docker run --rm \
         -v ($env.PWD + "/sql:/flyway/sql") \
         -v ($env.PWD + "/conf:/flyway/conf") \
         -v ($env.PWD + "/data:/flyway/data") \
         flyway/flyway:8.5.12-alpine \
         -user=postgres -password={{ password }} \
         -url=jdbc:postgresql://host.docker.internal:{{ port }}/settings \
         migrate

# Generate a backup of the Settings table on an instance of PostgresQL
db-backup password='password' port='5432':
  docker run --rm -it --entrypoint= -e PGPASSWORD={{ password }} postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab /bin/sh -c \
  'pg_dump -h host.docker.internal -p {{ port }} -U postgres settings' \
  | save --raw $"(date now | date format "%Y-%m-%d")-settings.db"

# Restores a backup of the Settings table on an instance of PostgresQL
db-restore backup password='password' port='5432':
  docker run --rm -it --entrypoint= -e PGPASSWORD={{ password }} -v {{ backup }}:/tmp/backup.db postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab bash -c \
  'psql -h host.docker.internal -p {{ port }} -U postgres settings < /tmp/backup.db'
