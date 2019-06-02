@echo off

REM Bot variables, required
SET BOT_TOKEN=
SET DATA_DIR=.
SET DISCORD_WEBHOOK=
SET PORT=

REM Minio Cloud Storage variables, required
SET DS_ACCESS_KEY=
SET DS_BASEURL=
SET DS_BUCKET=
SET DS_HOST=
SET DS_SECRET_KEY=

REM Rollbar variables, optional
SET ROLLBAR_ENV=
SET ROLLBAR_TOKEN=

@echo.
echo ============================== Starting Bot ==============================
echo    Version:  ${version}
echo Build Date:  ${timestamp}
echo   Revision:  https://github.com/guacamoledragon/throw-voice/commit/${revision}
echo ==========================================================================
@echo.

java -Xmx512m -cp ${name}-${version}.jar;lib\* tech.gdragon.App
