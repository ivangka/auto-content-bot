FROM openjdk:17-jdk-alpine
RUN apk --no-cache add curl
RUN apk add --no-cache bash

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
RUN chown appuser:appgroup /app

COPY target/*.jar app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]