FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY scim-server-model/pom.xml scim-server-model/
COPY scim-server-api/pom.xml scim-server-api/
COPY scim-server-mgmt/pom.xml scim-server-mgmt/
COPY scim-validator/pom.xml scim-validator/
COPY scim-validator-mgmt/pom.xml scim-validator-mgmt/
RUN mvn -pl scim-server-mgmt -am -DskipTests dependency:go-offline -B

COPY scim-server-model/src scim-server-model/src
COPY scim-server-api/src scim-server-api/src
COPY scim-server-mgmt/src scim-server-mgmt/src
RUN mvn -pl scim-server-mgmt -am -DskipTests package -B

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --from=build /workspace/scim-server-mgmt/target/*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
