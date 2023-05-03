FROM maven:3.9.1-eclipse-temurin-17-alpine as builder

WORKDIR /app

COPY pom.xml .
RUN mvn -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies

COPY LICENSE .
COPY src src

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
RUN mvn -B -Dversion="${VERSION}" -Dtimestamp="${BUILD_DATE}" -Drevision="${VCS_REF}" package -DskipTests

FROM curlimages/curl:latest as deps

WORKDIR /home/curl_user

ENV SDK_VERSION 1.3.0

RUN curl -Lo agent.jar https://github.com/honeycombio/honeycomb-opentelemetry-java/releases/download/v${SDK_VERSION}/honeycomb-opentelemetry-javaagent-${SDK_VERSION}.jar

# https://console.cloud.google.com/gcr/images/distroless/global/java17@sha256:0aea893ebf78c9d8111d709efd2bd3c6b0975d07fad11317355a2dad032823fc/details?tab=vulnz
FROM gcr.io/distroless/java17@sha256:0aea893ebf78c9d8111d709efd2bd3c6b0975d07fad11317355a2dad032823fc
LABEL maintainer="Jose V. Trigueros <jose@gdragon.tech>"

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="pawa" \
      org.label-schema.description="Simple audio recording for Discord." \
      org.label-schema.url="https://pawa.im" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://gitlab.com/pawabot/pawa" \
      org.label-schema.vendor="Guacamole Dragon, LLC" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

ENTRYPOINT []

EXPOSE 8080

ENV APP_DIR /app
ENV VERSION $VERSION

WORKDIR $APP_DIR
COPY --from=deps /home/curl_user/agent.jar .
COPY --from=builder /app/target/pawa-release/lib lib
COPY --from=builder /app/target/pawa-release/*.jar .

CMD ["java", "-javaagent:agent.jar", "-cp", "*:lib/*", "tech.gdragon.App"]
