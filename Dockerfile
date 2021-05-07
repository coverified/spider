FROM registry.gitlab.com/coverified/infrastructure/scala-base:latest

COPY . $WORKDIR

RUN ./gradlew shadowJar \
    --no-daemon \
    -Dorg.gradle.jvmargs="-XX:+UseContainerSupport -Xmx1024m -XX:MaxPermSize=256m"

ARG PROCECT_NAME
ARG CLASS_NAME
ARG SENTRY_DSN
ARG API_URL

ENV PROCECT_NAME=$PROCECT_NAME
ENV CLASS_NAME=$CLASS_NAME
ENV SENTRY_DSN=$SENTRY_DSN
ENV API_URL=$API_URL

CMD [ \
    "java", \
    "--add-opens", \
    "java.base/jdk.internal.misc=ALL-UNNAMED", \
    "-Dio.netty.tryReflectionSetAccessible=true", \
    "--illegal-access=warn", \
    "-cp", \
    "/app/build/libs/$PROCECT_NAME-0.1-SNAPSHOT-all.jar", \
    "$CLASS_NAME" \
    ]
