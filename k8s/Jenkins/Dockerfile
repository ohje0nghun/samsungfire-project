FROM jenkins/jenkins:2.492.1-lts

# ✅ Root 권한 최소화 (기본적으로 `jenkins` 사용자 유지)
USER root

# ✅ 필수 패키지만 설치 (git, curl, kubectl, helm, docker, podman)
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    curl \
    software-properties-common \
    && rm -rf /var/lib/apt/lists/*

# ✅ Helm 설치 (Kubernetes 관련)
RUN curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# ✅ kubectl 설치 (버전 명시적 지정)
RUN KUBECTL_VERSION="v1.28.0" && \
    curl -LO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && \
    mv kubectl /usr/local/bin/kubectl

# ✅ Docker 설치
RUN curl -fsSL https://get.docker.com | bash

# ✅ Podman 설치 (공식 패키지 사용)
RUN apt-get update && \
    apt-get install -y --no-install-recommends podman && \
    rm -rf /var/lib/apt/lists/*

# ✅ Jenkins 실행을 위한 권한 조정 (보안 강화)
RUN chown -R jenkins:jenkins /var/jenkins_home \
    && chmod -R 700 /var/jenkins_home

# ✅ 환경 변수 최소화 (기본 사용자 제거)
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc_configs

# ✅ Root 권한 제거 및 jenkins 사용자로 실행
USER jenkins

# ✅ Jenkins 실행
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/jenkins.sh"]
