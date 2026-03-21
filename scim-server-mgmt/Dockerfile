FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY scim-server-common/pom.xml scim-server-common/
COPY scim-server-api/pom.xml scim-server-api/
COPY scim-server-mgmt/pom.xml scim-server-mgmt/
COPY scim-validator/pom.xml scim-validator/
COPY scim-validator-mgmt/pom.xml scim-validator-mgmt/
RUN mvn -pl scim-server-mgmt -am -DskipTests dependency:go-offline -B

COPY scim-server-common/src scim-server-common/src
COPY scim-server-api/src scim-server-api/src
COPY scim-server-mgmt/src scim-server-mgmt/src
RUN mvn -pl scim-server-mgmt -am -DskipTests package -B

FROM dhi.io/eclipse-temurin:17-debian13
WORKDIR /app
COPY --from=build /workspace/scim-server-mgmt/target/*.jar app.jar
USER nonroot
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
