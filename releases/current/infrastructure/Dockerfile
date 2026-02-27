# Use the same JDK 21 base we verified in the startup scripts
FROM eclipse-temurin:21-jre-alpine

# Define the Sovereign Root
ENV REVEILA_HOME=/reveila
WORKDIR /reveila

# Create necessary directories inside the container
RUN mkdir -p /reveila/bin /reveila/libs /reveila/configs /reveila/data /reveila/logs /reveila/resources

# The JAR is mapped via volumes in Compose for development flexibility, 
# but we set the entrypoint to look in the mapped /libs folder.
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-DREVEILA_HOME=/reveila", "-jar", "/reveila/libs/reveila-suite-fat.jar"]