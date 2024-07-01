## -*- dockerfile-image-name: "registry.gitlab.com/pawabot/pawa" -*-
FROM curlimages/curl:latest as deps

WORKDIR /home/curl_user

ENV SDK_VERSION 2.3.0

RUN curl -Lo agent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${SDK_VERSION}/opentelemetry-javaagent.jar

FROM maven:3.9.7-eclipse-temurin-21-alpine as builder

WORKDIR /app

COPY pom.xml .
RUN mvn -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies

COPY LICENSE .
COPY src src

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
RUN mvn -B -Dversion="${VERSION:-dev}" -Dtimestamp="${BUILD_DATE}" -Drevision="${VCS_REF}" package -DskipTests

FROM gcr.io/distroless/java21
LABEL maintainer="Jose V. Trigueros <jose@gdragon.tech>"

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="pawa" \
      org.label-schema.description="Audio recording for Discord." \
      org.label-schema.url="https://pawa.im" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://gitlab.com/pawabot/pawa" \
      org.label-schema.vendor="Guacamole Dragon, LLC" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

ENTRYPOINT []

ENV APP_DIR /app
ENV VERSION ${VERSION:-dev}

WORKDIR $APP_DIR
COPY --from=deps /home/curl_user/agent.jar .
COPY --from=builder /app/target/pawa-release/lib lib
COPY --from=builder /app/target/pawa-release/*.jar .

CMD ["java", "-javaagent:agent.jar", "-cp", "*:lib/*", "tech.gdragon.App"]
