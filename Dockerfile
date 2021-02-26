FROM openjdk:8-slim

RUN apt-get update \
    && apt-get install -y \
    wget \
    grep \
    procps \
    && rm -rf /var/lib/apt/lists/*

RUN cd /tmp
RUN wget https://downloads.lightbend.com/scala/2.13.5/scala-2.13.5.deb
RUN dpkg -i scala-2.13.5.deb

RUN cd /usr/local/bin \
    && wget https://raw.githubusercontent.com/adamdehaven/fetchurls/master/fetchurls.sh \
    && sed -i -e 's{#!/bin/sh{#!/bin/bash{' fetchurls.sh \
    && chmod +x fetchurls.sh

COPY . /app

RUN cd /app \
    && ./gradlew clean build
