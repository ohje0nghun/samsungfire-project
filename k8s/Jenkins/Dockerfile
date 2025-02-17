FROM jenkins/jenkins:2.401.1

USER root
RUN apt-get update && \
    apt-get install -y docker.io && \
    apt-get install -y vim && \
    apt-get install -y net-tools
USER jenkins
