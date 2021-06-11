FROM registry.gitlab.com/coverified/infrastructure/scala-base:latest

ARG PROJECT_NAME
ARG PROJECT_VERSION
ARG MAIN_CLASS
ARG SENTRY_DSN

COPY build/libs/$PROJECT_NAME-$PROJECT_VERSION-all.jar $WORKDIR

ENV PROJECT_NAME=$PROJECT_NAME
ENV PROJECT_VERSION=$PROJECT_VERSION
ENV MAIN_CLASS=$MAIN_CLASS
ENV SENTRY_DSN=$SENTRY_DSN

CMD [ \
    "java", \
    "--add-opens", \
    "java.base/jdk.internal.misc=ALL-UNNAMED", \
    "-Dio.netty.tryReflectionSetAccessible=true", \
    "--illegal-access=warn", \
    "-cp", \
    "${WORKDIR}/${PROJECT_NAME}-${PROJECT_VERSION}-all.jar", \
    "${MAIN_CLASS}" \
    ]
