FROM openjdk:8
MAINTAINER Jose V. Trigueros <jose@gdragon.tech>

ADD target/lib /usr/share/throw-voice/lib
ADD target/throw-voice-*.jar /usr/share/throw-voice/throw-voice.jar

WORKDIR /usr/share/throw-voice

ENTRYPOINT ["/usr/bin/java", "-cp", "throw-voice.jar:lib/*", "tech.gdragon.App"]
