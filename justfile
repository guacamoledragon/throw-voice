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
apply-pg-migrations password='password' port='5432':
  docker run --rm \
         -v ($env.PWD + "/sql:/flyway/sql") \
         -v ($env.PWD + "/conf:/flyway/conf") \
         -v ($env.PWD + "/data:/flyway/data") \
         flyway/flyway:8.5.12-alpine \
         -user=postgres -password={{ password }} \
         -url=jdbc:postgresql://host.docker.internal:{{ port }}/settings \
         migrate
