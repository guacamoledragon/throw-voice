FROM openjdk:8-jre-slim

ENV VERSION 0.32.8

WORKDIR /app

ADD https://raw.githubusercontent.com/metabase/metabase/v$VERSION/bin/start /app/bin/
ADD http://downloads.metabase.com/v$VERSION/metabase.jar /app/target/uberjar/

CMD ["bash", "/app/bin/start"]
