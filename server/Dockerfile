FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends libreoffice-writer qpdf python3 python3-pip fonts-liberation fonts-noto-core fonts-noto-extra fonts-nafees \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt /tmp/requirements.txt
RUN pip3 install --break-system-packages --no-cache-dir -r /tmp/requirements.txt
RUN useradd --system --create-home --uid 10001 fileflow
WORKDIR /app
COPY --from=build /build/target/fileflow-processing-0.2.0.jar app.jar
USER fileflow
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-Djava.io.tmpdir=/tmp","-jar","/app/app.jar"]
