FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/task-management-system-1.0.0.jar /app/task-management-system-1.0.0.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "task-management-system-1.0.0.jar" ]