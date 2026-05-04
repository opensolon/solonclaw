FROM node:20-bookworm-slim AS frontend

WORKDIR /workspace/web

COPY web/package*.json /workspace/web/
RUN npm ci

COPY web /workspace/web
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY config.example.yml /workspace/config.example.yml
COPY src /workspace/src
COPY --from=frontend /workspace/web/dist /workspace/web/dist

RUN mvn -DskipTests -Dskip.web.build=true package \
    && cp "$(find target -maxdepth 1 -type f -name 'solon-claw-*.jar' ! -name 'original-*' | head -n 1)" /tmp/solon-claw.jar

FROM eclipse-temurin:17-jdk

WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Asia/Shanghai \
    PYTHONIOENCODING=UTF-8 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        git \
        curl \
        wget \
        jq \
        less \
        nano \
        vim-tiny \
        procps \
        psmisc \
        net-tools \
        iputils-ping \
        dnsutils \
        file \
        tree \
        unzip \
        zip \
        ca-certificates \
        tzdata \
        locales \
        gosu \
        tini \
        fontconfig \
        python3 \
        python3-pip \
        python3-venv \
        nodejs \
        npm \
        fonts-arphic-gbsn00lp \
        fonts-noto-cjk \
    && locale-gen C.UTF-8 \
    && fc-cache -f \
    && update-ca-certificates \
    && ln -sf /usr/bin/python3 /usr/local/bin/python \
    && ln -sf /usr/bin/pip3 /usr/local/bin/pip \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /tmp/solon-claw.jar /app/solon-claw.jar
COPY docker/entrypoint.sh /app/docker-entrypoint.sh

RUN groupadd -g 10000 solonclaw \
    && useradd -u 10000 -g solonclaw -m -d /home/solonclaw solonclaw \
    && mkdir -p /app/runtime \
    && sed -i 's/\r$//' /app/docker-entrypoint.sh \
    && chmod 755 /app/docker-entrypoint.sh \
    && chmod -R a+rX /app \
    && chown -R solonclaw:solonclaw /app/runtime

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "-g", "--", "/app/docker-entrypoint.sh"]
