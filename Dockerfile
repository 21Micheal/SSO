FROM maven:3.9-eclipse-temurin-17 AS provider-build
WORKDIR /build/dms-keycloak-provider
COPY providers/dms-keycloak-provider/pom.xml .
COPY providers/dms-keycloak-provider/src ./src
RUN mvn -q -DskipTests package

FROM quay.io/keycloak/keycloak:26.0 AS keycloak-build
COPY --from=provider-build /build/dms-keycloak-provider/target/dms-keycloak-provider-*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.0
COPY --from=keycloak-build /opt/keycloak/ /opt/keycloak/
