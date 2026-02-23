# Generic JRE-21 environment for Reveila
FROM eclipse-temurin:21-jre-alpine
WORKDIR /reveila

# Install netcat (nc) for the startup scripts' port checks
RUN apk add --no-cache netcat-openbsd

# Environment Defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV DB_HOST=reveila-db-prod
# Required for the application to find its home directory
ENV REVEILA_HOME=/reveila

# Expose the API port
EXPOSE 8080

# Launch using the mounted centralized startup script
# Relying on docker-compose to mount the bin folder
ENTRYPOINT ["/bin/sh", "/reveila/bin/startup.sh"]
