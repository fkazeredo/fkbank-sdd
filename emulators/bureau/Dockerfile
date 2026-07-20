# FKBANK KYC bureau emulator.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Resolve dependencies against the POM alone first: a source change then rebuilds in seconds
# instead of re-downloading the world.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in the module's own `verify` and in CI. Skipping them here does not skip a gate: an
# image is only built from a commit whose build already went green.
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Patch the base image's OS packages before dropping privileges. Base images lag behind Alpine's
# security updates, and the supply-chain gate fails the build on HIGH/CRITICAL CVEs in them - so
# this is what keeps the gate satisfied without weakening it. It does make the image contents
# vary with build date; that is the accepted trade-off for shipping patched libraries.
RUN apk --no-cache upgrade \
 # Never run as root: a container escape should not start with uid 0.
 && addgroup -S fkbank && adduser -S fkbank -G fkbank
USER fkbank

COPY --from=build /app/target/*.jar app.jar

EXPOSE 9101

# The control API is the emulator's own surface and answers 200 as soon as the service is really
# serving, which makes it the honest liveness signal here - there is no protected route to probe.
# busybox wget is what this image has; 127.0.0.1 and never `localhost`, because inside the
# container localhost resolves to IPv6 ::1 first, where the server is not listening.
HEALTHCHECK --interval=5s --timeout=3s --retries=40 \
  CMD wget -q -O /dev/null http://127.0.0.1:9101/control/scenario || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
