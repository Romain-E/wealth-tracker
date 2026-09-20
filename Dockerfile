# syntax=docker/dockerfile:1.7

# =============================================================================================
# Build stage.
#
# The image is built from source, so `docker compose up --build` works on a machine with nothing
# installed but Docker. Tests are not run here: they start containers of their own
# (Testcontainers), and CI runs them before it builds this image.
# =============================================================================================
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependencies first. This layer only changes when the pom does, so a code change rebuilds in
# seconds instead of downloading every dependency again. The Maven repository lives in a cache
# mount: it survives between builds without ever being written into an image layer.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --quiet dependency:go-offline

COPY src src

# The git metadata is bind-mounted rather than copied: the build reads the commit id from it for
# /actuator/info, and it never becomes part of any layer.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=bind,source=.git,target=/workspace/.git \
    ./mvnw --batch-mode --quiet package -DskipTests \
 && cp target/patrimoine-api-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# =============================================================================================
# Runtime stage: a JRE, the application split into layers, nothing else.
# =============================================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# A fixed, numeric, unprivileged user. Kubernetes can only enforce runAsNonRoot when the image's
# USER is numeric; a name would make it refuse to start the pod.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H app

WORKDIR /app

# Least volatile first. Dependencies change a few times a year, the application on every commit,
# so a new release usually ships only the last, small layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# The files stay owned by root: the process can read its code but not rewrite it.
USER 10001:10001

# 8080 serves the API. 8081 serves health and build information and is never exposed publicly.
EXPOSE 8080 8081

# Sized from the container's memory limit rather than the host's. On an out-of-memory error the
# JVM exits, so the orchestrator restarts a clean process instead of keeping a damaged one alive.
# Set through JAVA_TOOL_OPTIONS so a deployment can override it without rebuilding.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# No HEALTHCHECK here: Kubernetes ignores it and uses its own probes. Compose defines one.
ENTRYPOINT ["java", "-jar", "application.jar"]
