# 🔒 Multi-Tenant Security Hardening - Implementação Estrutural

## Status: PHASE 1 - STRUCTURAL PREVENTION

---

## 📋 ENTIDADES QUE PRECISAM DE TENANT_ID

### ✅ JÁ COM TENANT_ID
- User.java ✅ (tem campo, precisa @Filter)
- Lead.java ✅ (tem campo + @FilterDef + @Filter)

### ❌ PRECISA ADICIONAR TENANT_ID
- VendorLead.java (falta tenant_id + @Filter)
- Vendor.java (falta tenant_id + @Filter)
- UserSession.java (tem user_id, precisa tenant_id direto + @Filter)
- Payment.java (falta tenant_id + @Filter)
- Setting.java (falta tenant_id + @Filter)

### 🟡 ENTIDADES DE AUDITORIA (talvez não precise)
- AuditLog.java
- SecurityAuditLog.java
- LoginAudit.java
- VendorAuditLog.java

---

## 📋 ENTIDADES QUE HERDAM ISOLAMENTO (via FK)
- LeadStatusHistory → Lead.tenant_id (via leadId)
- VendorLeadStageHistory → VendorLead (se tiver tenant)
- VendorLeadMessage → VendorLead (se tiver tenant)
- VendorLeadConversation → VendorLead (se tiver tenant)
- SubscriptionHistory → Vendor/User via FK

---

## 🎯 IMPLEMENTAÇÃO - 9 ETAPAS

### ETAPA 1: Adicionar tenant_id em entidades críticas
- User.java (add @Filter ao tenant_id existente)
- VendorLead.java (add column + @Filter)
- Vendor.java (add column + @Filter)
- UserSession.java (add direct tenant_id + @Filter)
- Payment.java (add column + @Filter)
- Setting.java (add column + @Filter)

### ETAPA 2: Aplicar @FilterDef/@Filter GLOBAL
- Criar base @FilterDef em User.java (reutilizável)
- Copiar @Filter para todas entidades

### ETAPA 3: Criar HibernateFilterService (bean Spring)
- Ativar filter automático por request
- Integrar com TenantFilter existente

### ETAPA 4: Reforçar TenantContext.clear()
- Garantir finally block no TenantFilter HTTP
- Validar que ThreadLocal é limpo

### ETAPA 5: Validação fail-fast
- Em @PrePersist: validar tenant_id NOT NULL
- Em repositories: validar tenant no save()

### ETAPA 6: Proteger queries custom
- Auditoria em @Query
- Reescrever que faltam tenant WHERE

### ETAPA 7: Documentar regra no código
- Adicionar JavaDoc em cada entidade
- Adicionar exemplo

### ETAPA 8: Async/Background handling
- Verificar se há @Async que precisa tenant propagation
- Criar AsyncTenantPropagation helper

### ETAPA 9: Teste anti-regressão
- Teste que cria lead em tenant_A
- Tenta listar em tenant_B → vazio
- Se quebrar → teste falha

---

## ✅ RESULTADO

De: Sistema funciona
Para: **Sistema NÃO PODE quebrar sem explodir erro**

---

## 🔧 PRÓXIMAS AÇÕES

1. Migração V85+: Adicionar tenant_id em VendorLead, Vendor, UserSession, Payment, Setting
2. Código: Adicionar @FilterDef/@Filter em User.java
3. Código: Criar HibernateFilterService
4. Código: Validações fail-fast
5. Testes: Anti-regressão

