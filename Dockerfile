FROM registry.gitlab.com/coverified/infrastructure/scala-base:latest

COPY build/libs/$PROJECT_NAME-0.1-SNAPSHOT-all.jar $WORKDIR

ARG PROJECT_NAME
ARG PROJECT_VERSION
ARG MAIN_CLASS
ARG SENTRY_DSN
ARG API_URL

ENV SENTRY_DSN=$SENTRY_DSN
ENV API_URL=$API_URL

CMD [ \
    "java", \
    "--add-opens", \
    "java.base/jdk.internal.misc=ALL-UNNAMED", \
    "-Dio.netty.tryReflectionSetAccessible=true", \
    "--illegal-access=warn", \
    "-cp", \
    "$WORKDIR/$PROJECT_NAME-$PROJECT_VERSION-all.jar", \
    "$MAIN_CLASS" \
    ]
