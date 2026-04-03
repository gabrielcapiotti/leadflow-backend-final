# 🔍 Investigação: Bug de Corrupção de tenantId UUID

## 📋 Sumário do Problema

**Evidência:**
```
Tenant correto criado:  a78256f7-aca6-4c8d-ac9a-890ce523d609
Log do SubscriptionService: a788256f7... (SEM HÍFENS, com "8" DUPLICADO)
```

**Padrão:**
- Original: `a78256f7` (8 chars)
- Corrompido: `a788256f7` (9 chars - duplicou o "8")

---

## ✅ Áreas Verificadas (Sem Problemas)

### 1. **JwtService** ✅
- Serialização: Usa `.toString()` corretamente
- Deserialização: Usa `SafeUUIDDeserializer` com validação rigorosa
- **Resultado:** Sem problemas

### 2. **JwtAuthenticationFilter** ✅
- Converte UUID com double-check
- Valida roundtrip após conversão
- Passa UUID (não String) para `TenantContext.setTenant()`
- **Resultado:** Sem problemas

### 3. **TenantContext** ✅
- Aceita apenas UUID (não String)
- Armazena em ThreadLocal
- **Resultado:** Sem problemas

### 4. **AuthController**
- `register()`: Passa UUID corretamente
- `login()`: Usa `user.getTenantId()` diretamente
- `createSession()`: Valida UUID format antes de usar
- **Resultado:** Sem problemas

### 5. **AuthService.registerUser()** ✅
- Recebe UUID como parâmetro
- Passa para `user.setTenantId(tenantId)`
- Chama `vendorService.createVendor(user)` com o user inteiro
- **Resultado:** Sem problemas

### 6. **VendorService.createVendor()** ✅
- Valida `user.getTenantId()` não-nulo
- Passa UUID para `vendor.setTenantId()`
- **Resultado:** Sem problemas

### 7. **SubscriptionService.createDefaultSubscription()** ✅
- Recebe UUID como parâmetro
- Log: `log.info("🔄 Creating default subscription for tenant: {}", tenantId);`
- Seria logado como UUID válido com hífens
- **Resultado:** Sem problemas aparentes em código

### 8. **UsageService.initializeUsage()** ✅
- Recebe UUID como parâmetro
- Não há manipulação de string
- **Resultado:** Sem problemas

### 9. **UserSessionService.createSession()** ✅
- Valida UUID format com regex
- Compara `persisted.equals(tenantId)` após persistência
- Lança exceção se UUID foi corrompido pela persistência
- **Resultado:** Sem problemas

### 10. **SafeUUIDDeserializer** ✅ (Reforçado)
- Detecta 2+ caracteres repetidos
- Valida comprimento (36 chars)
- Deteta espaços e caracteres invisíveis
- Roundtrip verification
- **Resultado:** Sem problemas

---

## 🔴 Hipóteses Restantes

### Hipótese 1: Bug no Hibernate/JPA
**Possibilidade:** Custom UUID converter no Hibernate
```java
// Procurar por:
@Convert(converter = ...)
@org.hibernate.type.descriptor.java.UUIDTypeDescriptor
```
**Status:** Não verificado ainda

### Hipótese 2: Bug no PostgreSQL ou Flyway
**Possibilidade:** Coluna `tenant_id` definida com tipo errado
- Deveria ser: `UUID`
- Pode estar como: `VARCHAR`, `CHAR(36)`, etc.
**Status:** Não verificado ainda

### Hipótese 3: Bug no JSON Deserialization
**Possibilidade:** Deserializer customizado que muta String
**Status:** Não verificado ainda

### Hipótese 4: Bug no LoggerFactory de formatação
**Possibilidade:** Algo está formatando o UUID de forma errada ao logar
**Status:** Improvável (SLF4J é standard)

### Hipótese 5: Problema no teste PowerShell
**Possibilidade:** O teste está "vendo" corrupção que na verdade vem de outro lugar
**Status:** Possível

---

## 🔍 Próximos Passos a Verificar

1. **Verificar Hibernate Configuration:**
   ```java
   // Em: src/main/java/com/leadflow/backend/
   // Procurar por:
   - @Convert anotações em entidades
   - application.properties / application.yml
   - HibernateConfigXXX
   ```

2. **Verificar schema SQL:**
   ```sql
   -- Procurar em: src/main/resources/db/migration/
   -- Ver como as colunas tenant_id foram criadas
   -- Devem ser: UUID type, não VARCHAR
   ```

3. **Verificar Entities:**
   ```java
   // Em: src/main/java/com/leadflow/backend/entities/
   - Subscription.java - OK ✅
   - Vendor.java - Verificar
   - User.java - Verificar
   - Procurar por @Convert(converter = ...)
   ```

4. **Executar teste com logging de DEBUG:**
   ```java
   // Em: AuthController.register()
   log.info("RAW TENANT UUID: {}", tenantId);
   log.info("TENANT CLASS: {}", tenantId.getClass());
   log.info("TENANT BYTES: {}", Arrays.toString(tenantId.toString().getBytes()));
   ```

---

## 📊 Status da Investigação

| Área | Status | Confiança |
|------|--------|-----------|
| JWT | ✅ OK | 100% |
| Spring Security | ✅ OK | 100% |
| Auth Layer | ✅ OK | 100% |
| BusinessLogic | ✅ OK | 95% |
| Database Schema | ❌ NÃO VERIFICADO | 0% |
| Hibernate Config | ❌ NÃO VERIFICADO | 0% |
| Entity Converters | ❌ NÃO VERIFICADO | 0% |

---

## 🎯 Recomendação Imediata

**Procurar especificamente em:**

1. `application.yml` / `application.properties`
   - Configurações de UUID no Hibernate
   
2. Todas as entidades em `src/main/java/com/leadflow/backend/entities/`
   - Especialmente aquelas com `tenant_id`
   - Procurar por `@Convert`

3. `src/main/resources/db/migration/`
   - Schema definition do `tenant_id`
   - Deve ser `UUID type`

4. `MultiTenantHibernateConfigBackend.java`
   - Pode ter converters customizados

---

## 🧪 Teste de Validação

Após correção, executar:
```powershell
./test-roles-management_SUCESS.ps1
```

Esperar: **6/6 testes passando** ✅
