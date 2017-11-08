FROM maven:3.5-jdk-9-slim
WORKDIR /src
COPY . /src
RUN mvn package

FROM openjdk:9-jre-slim
MAINTAINER Jose V. Trigueros <jose@gdragon.tech>

WORKDIR /root/

COPY --from=0 /src/target/lib lib/
COPY --from=0 /src/target/throw-voice-*.jar throw-voice.jar
VOLUME recordings/

CMD ["/usr/bin/java", "-cp", "throw-voice.jar:lib/*", "tech.gdragon.App"]
