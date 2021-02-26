FROM debian:10

RUN apt-get update \
    && apt-get install -y \
    wget \
    grep \
    procps \
    default-jdk \
    && rm -rf /var/lib/apt/lists/*
    && cd /tmp \
    && wget https://downloads.lightbend.com/scala/2.13.5/scala-2.13.5.deb \
    && dpkg -i scala-2.13.5.deb

COPY . /app

RUN cd /app \
    && ./gradlew shadowJar

ENTRYPOINT ["java", "-jar", "spider_service-0.1-SNAPSHOT-all.jar"]
