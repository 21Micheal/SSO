FROM maven:3.9-eclipse-temurin-17 AS dms-provider-build
WORKDIR /build/dms-keycloak-provider
COPY providers/dms-keycloak-provider/pom.xml .
COPY providers/dms-keycloak-provider/src ./src
RUN mvn -q -DskipTests package

FROM maven:3.9-eclipse-temurin-17 AS financial-provider-build
WORKDIR /build/financial-keycloak-provider
COPY providers/financial-keycloak-provider/pom.xml .
COPY providers/financial-keycloak-provider/src ./src
RUN mvn -q -DskipTests package

FROM quay.io/keycloak/keycloak:26.0 AS keycloak-build
COPY --from=dms-provider-build /build/dms-keycloak-provider/target/dms-keycloak-provider-*.jar /opt/keycloak/providers/
COPY --from=financial-provider-build /build/financial-keycloak-provider/target/financial-keycloak-provider-*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.0
COPY --from=keycloak-build /opt/keycloak/ /opt/keycloak/
