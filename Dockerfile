FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package \
    && cp target/*-jar-with-dependencies.jar /app.jar

FROM eclipse-temurin:17-jre
WORKDIR /data

COPY --from=build /app.jar /app/app.jar

ENV JAVA_OPTS="-Xmx256m" \
    TZ=Europe/Moscow

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
