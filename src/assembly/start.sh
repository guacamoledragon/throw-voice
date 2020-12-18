#! /bin/bash

# Bot variables, required
export BOT_TOKEN=
export BOT_DATA_DIR=./data
export DISCORD_WEBHOOK=
export BOT_HTTP_PORT=

# Minio Cloud Storage variables, required
export DS_ACCESS_KEY=
export DS_BASEURL=
export DS_BUCKET=
export DS_HOST=
export DS_SECRET_KEY=

echo ============================== Starting Bot ==============================
echo    Version:  ${version}
echo Build Date:  ${timestamp}
echo        URL:  https://pawa.im/releases/${version}
echo ==========================================================================

java -Xmx512m -cp ${name}-${version}.jar:lib/* tech.gdragon.App
