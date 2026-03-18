FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml ./
COPY src ./src

RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/target/solonclaw.jar /app/solonclaw.jar

EXPOSE 12345

ENV JAVA_OPTS=""
ENV APP_ARGS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/solonclaw.jar $APP_ARGS"]
