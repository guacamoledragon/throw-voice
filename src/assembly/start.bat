@echo off

REM Bot specific variables, required
SET BOT_TOKEN=
SET CLIENT_ID=
SET DATA_DIR=
SET PORT=

REM Storage specific variables, required
SET B2_APP_KEY=
SET B2_ACCOUNT_ID=
SET B2_BASE_URL=
SET B2_BUCKET_ID=
SET B2_BUCKET_NAME=

REM Logging specific variables, optional
SET ROLLBAR_ENV=
SET ROLLBAR_TOKEN=

@echo.
echo ============================== Starting Bot ==============================
echo    Version:  ${version}
echo Build Date:  ${timestamp}
echo   Revision:  https://github.com/guacamoledragon/throw-voice/commit/${revision}
echo ==========================================================================
@echo.

java -Xmx512m --add-modules java.xml.bind -cp ${name}-${version}.jar;lib\* tech.gdragon.App
