# LeadFlow Backend - Kubernetes Deployment Guide

## 📋 Pré-requisitos

- Kubernetes 1.24+
- kubectl configurado
- Image registry acesso (DockerHub, ECR, etc)
- NGINX Ingress Controller
- Cert-manager (para SSL/TLS)

## 🚀 Instalação Rápida

### 1. Build e Push da Image Docker

```bash
# Build image
docker build -t leadflow-backend:1.0.0 .

# Tag para seu registr o
docker tag leadflow-backend:1.0.0 your-registry/leadflow-backend:1.0.0

# Push
docker push your-registry/leadflow-backend:1.0.0
```

### 2. Criar Namespace

```bash
kubectl apply -f k8s/namespace.yaml
```

### 3. Configurar Secrets (IMPORTANTE!)

```bash
# Edit secret.yaml com suas credenciais reais
kubectl apply -f k8s/secret.yaml

# Verificar
kubectl get secrets -n leadflow
```

### 4. Aplicar ConfigMap

```bash
kubectl apply -f k8s/configmap.yaml
```

### 5. Deploy PostgreSQL (StatefulSet)

```bash
kubectl apply -f k8s/statefulset-postgres.yaml

# Aguardar que o Pod esteja ready
kubectl wait --for=condition=ready pod -l app=postgres -n leadflow --timeout=300s
```

### 6. Deploy Application (Deployment)

```bash
kubectl apply -f k8s/deployment.yaml

# Verificar rollout status
kubectl rollout status deployment/leadflow-backend -n leadflow
```

### 7. Configurar Auto-scaling & Ingress

```bash
kubectl apply -f k8s/hpa-and-ingress.yaml

# Verificar HPA status
kubectl get hpa -n leadflow
```

### 8. Verificar Status Geral

```bash
# Todos os recursos
kubectl get all -n leadflow

# Pods com detalhe
kubectl get pods -n leadflow -o wide

# Logs
kubectl logs -f deployment/leadflow-backend -n leadflow

# Events
kubectl describe pod <pod-name> -n leadflow
```

## 🔒 Segurança - Mais Recomendações

### Use Sealed Secrets em Produção

```bash
# Instalar sealed-secrets
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.18.0/controller.yaml

# Criar sealed secret
echo -n "your-db-password" | kubectl create secret generic db-secret --dry-run=client --from-file=password=/dev/stdin -o yaml | kubeseal -f - > sealed-secret.yaml

# Aplicar
kubectl apply -f sealed-secret.yaml
```

### Network Policies

```bash
# Já incluído em hpa-and-ingress. yaml
# Restringe tráfego entre pods
```

## 📊 Monitoramento & Logging

### Prometheus Metrics

```bash
# Métricas disponíveis em:
# http://api.yourdomain.com/actuator/prometheus
```

### Log Aggregation  

```bash
#Exemplo com ELK Stack
#Configure em seu Filebeat/Logstash
```

## 🔄 CI/CD Integration

### GitHub Actions (Example)

```yaml
# .github/workflows/deploy-k8s.yml
name: Deploy to Kubernetes

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    
    - name: Build Docker image
      run: docker build -t your-registry/leadflow-backend:${{ github.sha }} .
    
    - name: Push to registry
      run: docker push your-registry/leadflow-backend:${{ github.sha }}
    
    - name: Deploy to K8s
      run: |
        kubectl set image deployment/leadflow-backend \
          leadflow-backend=your-registry/leadflow-backend:${{ github.sha }} \
          -n leadflow --record
```

## 🔧 Troubleshooting

### Pod não inicia

```bash
kubectl describe pod <pod-name> -n leadflow
kubectl logs <pod-name> -n leadflow
```

### Banco não conecta

```bash
# Verificar service DNS
kubectl run -it debug --image=ubuntu:22.04 -- bash
apt-get update && apt-get install -y postgresql-client
psql -h postgres.leadflow.svc.cluster.local -U leadflow_user -d leadflow
```

### Alterar replicas

```bash
kubectl scale deployment leadflow-backend --replicas=5 -n leadflow
```

### Rollback de deployment

```bash
kubectl rollout history deployment/leadflow-backend -n leadflow
kubectl rollout undo deployment/leadflow-backend -n leadflow
```

## 📝 Dicas de Produção

1. **Use PersistentVolumes** com backup automático
2. **Configure Resource Quotas** por namespace
3. **Use Network Policies** para isolar tráfego
4. **Configure readiness/liveness probes** (já inclusos)
5. **Use Ingress com TLS** (cert-manager)
6. **Configure Container Registry secrets**
7. **Implemente RBAC** adequado
8. **Use Resource limits** (já inclusos)
9. **Implemente Pod Disruption Budgets**
10. **Configure backup automático** do banco

## 📞 Suporte

Consulte a documentação oficial: https://kubernetes.io/docs/
