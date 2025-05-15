FROM tomcat:10.1.33-jdk21-temurin
RUN groupadd -r messenger && useradd -r -g messenger msguser


WORKDIR /app
COPY /target/messenger-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/messenger.war

RUN mkdir -p /app/uploads \
 && chown -R msguser:messenger /app/uploads \
 && chown -R msguser:messenger /usr/local/tomcat/webapps

EXPOSE 8080

USER msguser