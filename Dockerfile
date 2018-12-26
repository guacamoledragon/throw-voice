FROM maven:3.6-jdk-11 as builder
WORKDIR /app
COPY . /app

VOLUME /root/.m2

# Source: https://git.mikael.io/mikaelhg/broken-docker-jdk9-cacerts#tldr-workaround
RUN /usr/bin/printf '\xfe\xed\xfe\xed\x00\x00\x00\x02\x00\x00\x00\x00\xe2\x68\x6e\x45\xfb\x43\xdf\xa4\xd9\x92\xdd\x41\xce\xb6\xb2\x1c\x63\x30\xd7\x92' > /etc/ssl/certs/java/cacerts \
 && /var/lib/dpkg/info/ca-certificates-java.postinst configure \
 && mvn package

FROM openjdk:11-jre-slim
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

# Source: https://git.mikael.io/mikaelhg/broken-docker-jdk9-cacerts#tldr-workaround
RUN /usr/bin/printf '\xfe\xed\xfe\xed\x00\x00\x00\x02\x00\x00\x00\x00\xe2\x68\x6e\x45\xfb\x43\xdf\xa4\xd9\x92\xdd\x41\xce\xb6\xb2\x1c\x63\x30\xd7\x92' > /etc/ssl/certs/java/cacerts \
 && /var/lib/dpkg/info/ca-certificates-java.postinst configure

ENV APP_DIR /app
ENV DATA_DIR $APP_DIR/data

ENV JAVA_LIB_DIR lib/*
ENV JAVA_MAIN_CLASS tech.gdragon.App

WORKDIR $APP_DIR

COPY --from=builder /app/target/throw-voice-*-release.zip /tmp/throw-voice-release.zip
RUN unzip -d $APP_DIR /tmp/throw-voice-release.zip

ADD https://cdn.rawgit.com/fabric8io-images/run-java-sh/v1.2.0/fish-pepper/run-java-sh/fp-files/run-java.sh $APP_DIR

VOLUME $DATA_DIR

CMD ["sh", "run-java.sh"]
