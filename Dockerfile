## -*- dockerfile-image-name: "registry.gitlab.com/pawabot/pawa" -*-
FROM debian:11 as audiowaveform

RUN apt update \
 && apt install -y \
    git make cmake gcc g++ libmad0-dev \
    libid3tag0-dev libsndfile1-dev libgd-dev libboost-filesystem-dev \
    libboost-program-options-dev \
    libboost-regex-dev

RUN git clone -n https://github.com/bbc/audiowaveform.git \
 && cd audiowaveform \
 && git checkout 1.7.1 \
 && mkdir build \
 && cd build \
 && cmake -D ENABLE_TESTS=0 -D BUILD_STATIC=1 .. \
 && make -j $(nproc) \
 && make install

FROM curlimages/curl:latest as deps

WORKDIR /home/curl_user

ENV SDK_VERSION 2.2.0

RUN curl -Lo agent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${SDK_VERSION}/opentelemetry-javaagent.jar

FROM maven:3.9.1-eclipse-temurin-17-alpine as builder

WORKDIR /app

COPY pom.xml .
RUN mvn -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies

COPY LICENSE .
COPY src src

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
RUN mvn -B -Dversion="${VERSION:-dev}" -Dtimestamp="${BUILD_DATE}" -Drevision="${VCS_REF}" package -DskipTests

# https://console.cloud.google.com/gcr/images/distroless/global/java17-debian11@sha256:92f34f18951118ac1c8261c128cdf2001b4e74340050f557dcd1ac4ddd6a07ad/details?tab=vulnz
FROM gcr.io/distroless/java17-debian11@sha256:92f34f18951118ac1c8261c128cdf2001b4e74340050f557dcd1ac4ddd6a07ad
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
ENV VERSION ${VERSION:-dev}

WORKDIR $APP_DIR
COPY --from=audiowaveform /usr/local/bin/audiowaveform .
COPY --from=deps /home/curl_user/agent.jar .
COPY --from=builder /app/target/pawa-release/lib lib
COPY --from=builder /app/target/pawa-release/*.jar .

CMD ["java", "-javaagent:agent.jar", "-cp", "*:lib/*", "tech.gdragon.App"]
