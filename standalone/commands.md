# Gradle Commands

./gradlew.bat :app:run

## Gradle runArgs Project Argument

../standalone> ./gradlew.bat :app:run -PrunArgs="reveila.properties=file:///C:/IDE/Projects/Reveila-Suite/reveila/runtime-directory/configs/reveila.properties

## Run in Debug Mode

./gradlew :app:run --debug-jvm --no-daemon

## Build "Fat" JAR

./gradlew shadowJar
