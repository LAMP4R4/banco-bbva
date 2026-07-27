#Etapa 1: construccion builder
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

#cipoaar el wrapper de maven y los archivos de configuracion
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

#DAr permisos
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

#Etapa 2: Imagen final para la produccion (Ligera)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
#Copiar solo el archivo .jar generado en la etapa anterior
COPY --from=builder /app/target/*.jar app.jar
#Exponer el puerto (por defecto 8080)
EXPOSE 8080
#Comando para ejecutar la aplicacion
ENTRYPOINT [ "java", "-jar", "app.jar" ]