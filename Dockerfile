FROM maven:3.5-jdk-9-slim as builder
WORKDIR /src
COPY . /src

RUN apt-get update && apt-get install -y \
  git \
 && mvn package \
 && rm -rf /var/lib/apt/lists/*

FROM openjdk:9-jre-slim
MAINTAINER Jose V. Trigueros <jose@gdragon.tech>

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="throw-voice" \
      org.label-schema.description="A voice channel recording bot for Discord." \
      org.label-schema.url="https://www.pawabot.site" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/guacamoledragon/throw-voice" \
      org.label-schema.vendor="Guacamole Dragon, LLC" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

ENV APP_DIR /app
ENV DATA_DIR $APP_DIR/data

WORKDIR $APP_DIR

COPY --from=builder /src/target/lib $APP_DIR/lib/
COPY --from=builder /src/target/throw-voice-*.jar $APP_DIR/throw-voice.jar
VOLUME $DATA_DIR

CMD ["sh", "-c", "/usr/bin/java ${JAVA_OPTS} -cp throw-voice.jar:lib/* tech.gdragon.App"]
