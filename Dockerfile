FROM maven:3.6-jdk-11 as builder
WORKDIR /app
COPY . /app

RUN mvn package

FROM boxfuse/flyway as database

COPY sql /flyway/sql
COPY conf /flyway/conf

RUN ["flyway", "-url=jdbc:sqlite:/tmp/settings.db", "migrate"]

FROM openjdk:11-jre-slim
MAINTAINER Jose V. Trigueros <jose@gdragon.tech>

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="throw-voice" \
      org.label-schema.description="A voice channel recording bot for Discord." \
      org.label-schema.url="https://www.pawa.im" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/guacamoledragon/throw-voice" \
      org.label-schema.vendor="Guacamole Dragon, LLC" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

ENV APP_DIR /app
ENV DATA_DIR $APP_DIR/data

ENV JAVA_LIB_DIR lib/*
ENV JAVA_MAIN_CLASS tech.gdragon.App

WORKDIR $APP_DIR

COPY --from=builder /app/target/throw-voice-*-release.zip /tmp/throw-voice-release.zip
RUN unzip -d $APP_DIR /tmp/throw-voice-release.zip

CMD java ${JAVA_OPTS} -cp *:$JAVA_LIB_DIR $JAVA_MAIN_CLASS
