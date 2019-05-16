FROM maven:3.6-jdk-11 as builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY . .

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
RUN mvn -Dversion="${VERSION}" -Dtimestamp="${BUILD_DATE}" -Drevision="${VCS_REF}" package

FROM boxfuse/flyway as database

COPY sql /flyway/sql
COPY conf /flyway/conf

RUN ["flyway", "-url=jdbc:sqlite:/tmp/settings.db", "migrate"]

FROM gcr.io/distroless/java:11
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

ENTRYPOINT []

EXPOSE 8080

ENV APP_DIR /app
ENV DATA_DIR $APP_DIR/data
ENV VERSION $VERSION

WORKDIR $APP_DIR

COPY --from=builder /app/target/throw-voice-*-release/lib lib
COPY --from=builder /app/target/throw-voice-*-release/* .
COPY --from=database /tmp/settings.db $DATA_DIR/settings.db

CMD ["java", "-cp", "*:lib/*", "tech.gdragon.App"]
