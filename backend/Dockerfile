# FKBANK backend.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Resolve dependencies against the POM alone first: a source change then rebuilds in seconds
# instead of re-downloading the world.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in `verify-slice` and in CI against real infrastructure (Testcontainers needs a
# Docker daemon, which an image build does not have). Skipping them here does not skip a gate:
# an image is only built from a commit whose build already went green.
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

EXPOSE 8080

# A protected route answering 401 proves the application is SERVING, not merely that the port
# is open - so the check accepts 401 as readily as 200. busybox wget is what this image has;
# it exits non-zero on a 401, but the pipeline's status is grep's, so the check still passes.
HEALTHCHECK --interval=5s --timeout=3s --retries=40 \
  CMD wget -q -S -O /dev/null http://127.0.0.1:8080/api/me 2>&1 | grep -qE ' (401|200) ' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
