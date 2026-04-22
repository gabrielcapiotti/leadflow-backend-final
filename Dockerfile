# Multi-stage build para LeadFlow Backend
# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml e baixa dependências (layer de cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy código fonte
COPY src/ ./src/

# Build da aplicação (skip tests, serão rodados separadamente)
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Instalações essenciais
RUN apk add --no-cache \
    curl \
    dumb-init \
    ca-certificates

# Copy JAR da stage anterior
COPY --from=builder /app/target/leadflow-backend-*.jar app.jar

# Criar usuário não-root para segurança
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/actuator/health/live || exit 1

# Porta padrão 8080 (pode ser overridden por PORT)
# Development: 8080, Production: 8080 (via PORT env var)
EXPOSE ${PORT:-8080}

# entrypoint para graceful shutdown
ENTRYPOINT ["dumb-init", "--"]

# Start Spring Boot - using shell form to expand env vars safely
CMD sh -c 'java \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+PrintGCDetails \
     -Djava.awt.headless=true \
     -Duser.timezone=UTC \
     -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
     -Dserver.port=${PORT:-8080} \
     -jar app.jar'
