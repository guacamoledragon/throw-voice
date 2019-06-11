#! /bin/bash

# Bot variables, required
BOT_TOKEN=
DATA_DIR=./data
DISCORD_WEBHOOK=
PORT=

# Minio Cloud Storage variables, required
DS_ACCESS_KEY=
DS_BASEURL=
DS_BUCKET=
DS_HOST=
DS_SECRET_KEY=

# Rollbar variables, optional
ROLLBAR_ENV=
ROLLBAR_TOKEN=

echo ============================== Starting Bot ==============================
echo    Version:  ${version}
echo Build Date:  ${timestamp}
echo   Revision:  https://github.com/guacamoledragon/throw-voice/commit/${revision}
echo ==========================================================================

java -Xmx512m -cp ${name}-${version}.jar:lib/* tech.gdragon.App
