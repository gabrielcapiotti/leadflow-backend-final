# 🎯 LOCALIZAÇÃO EXATA DOS ERROS NO CÓDIGO

## 1. ⚠️ TenantFilter.java - TENTANDO RESOLVER TENANT MAS FALHANDO

**Arquivo:** `src/main/java/com/leadflow/backend/multitenancy/filter/TenantFilter.java`

### Linha 121-149: O PONTO DE FALHA
```java
121 | try {
122 |     String existingTenant = null;
123 |     try {
124 |         existingTenant = TenantContext.getTenant();
125 |     } catch (IllegalStateException ignored) {
126 |     }
127 | 
128 |     if (existingTenant != null && !existingTenant.isBlank()) {
129 |         logger.debug("Tenant already present in context: {}", ...);
130 |         filterChain.doFilter(request, response);
131 |         return;
132 |     }
133 |
134 |     /* AQUI TENTA RESOLVER */
135 |     String tenant = tenantResolver.resolveTenant(request);  // ← LINE 135: FALHA AQUI?
136 |
137 |     if (tenant == null || tenant.isBlank()) {
138 |         logger.warn("Tenant could not be resolved for request {}", request.getRequestURI());
139 |         response.sendError(HttpServletResponse.SC_BAD_REQUEST,
140 |             "Header 'X-Tenant-Id' is required");  // ← LINE 140: RETORNA 400
141 |         return;  // ← LINE 141: NUNCA SETA TENANTCONTEXT!
142 |     }
143 |
144 |     logger.debug("Tenant resolved: {}", LogSanitizer.sanitize(tenant));
145 |     TenantContext.setTenant(tenant);  // ← LINE 145: NUNCA CHEGA AQUI QUANDO FALHA
```

### O Problema
Se `tenantResolver.resolveTenant(request)` retorna NULL em linha 135:
- Entra no `if` linha 137
- Envia erro 400 linha 140
- Faz `return` linha 141
- **TenantContext.setTenant() NUNCA é chamado**
- TenantContext fica NULL para o resto da requisição

---

## 2. ⚠️ TenantResolver.java - NÃO CONSEGUE EXTRAIR TENANT

**Arquivo:** `src/main/java/com/leadflow/backend/multitenancy/resolver/TenantResolver.java`

### Linha 63-89: TENTANDO ENCONTRAR TENANT
```java
63 |     public String resolveTenant(HttpServletRequest request) {
64 |         // Step 1: Try JWT token first (PREFERRED)
65 |         String tenantFromJwt = extractTenantFromJwt(request);  // ← LINE 65: EXTRAI JWT
66 |         String tenantFromHeader = request.getHeader("X-Tenant-Id");
67 |
68 |         if (tenantFromJwt != null && !tenantFromJwt.isBlank()) {
69 |             // JWT token present - USE IT as source of truth
70 |             
71 |             // If header also provided, validate they match (security check)
72 |             if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
73 |                 if (!tenantFromJwt.equalsIgnoreCase(tenantFromHeader)) {
74 |                     throw new ResponseStatusException(
75 |                         HttpStatus.FORBIDDEN,
76 |                         "Tenant mismatch: JWT tenant does not match X-Tenant-Id header"
77 |                     );
78 |                 }
79 |             }
80 |             
81 |             return tenantFromJwt;  // ← LINE 81: RETORNA JWT TENANT
82 |         }
83 |
84 |         // Step 2: Fallback to X-Tenant-Id header (only if no JWT)
85 |         if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
86 |             return tenantFromHeader;  // ← LINE 86: RETORNA HEADER TENANT
87 |         }
88 |
89 |         // Step 3: No tenant found
90 |         throw new ResponseStatusException(
91 |             HttpStatus.UNAUTHORIZED,
92 |             "Missing tenant identification (JWT or X-Tenant-Id header required)"
93 |         );
94 |     }
```

### O Problema
Linha 65: `extractTenantFromJwt(request)` retorna NULL quando:
- Bearer token não está no header Authorization, OU
- JwtService.extractTenant(token) retorna NULL porque **JWT não tem claim "tenant"**

Se tenantFromJwt é NULL E tenantFromHeader tem valor → retorna header (FUNCIONA COM HEADER)
Se ambos NULL → lança exceção linha 90 (ERRO 401)

---

## 3. 🔴 JwtService.java - NÃO ADICIONA CLAIM "TENANT" AO JWT

**Arquivo:** `src/main/java/com/leadflow/backend/security/jwt/JwtService.java`

### Linha XXX: GERAÇÃO DO TOKEN (PROCURAR POR)

⚠️ **PROCURE POR ESTE PADRÃO:**
```java
public String generateToken(User user, String tenantId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("subject", ...);
    claims.put("userId", ...);
    // claims.put("tenant", tenantId);  // ← ESSA LINHA ESTÁ FALTANDO?
    claims.put("roles", ...);
    return createToken(claims, user.getEmail());
}
```

### Método extractTenant (PROCURAR POR)
```java
public String extractTenant(String token) {
    return extractClaim(token, claims -> claims.get("tenant", String.class));
    // ← SE NÃO TEM CLAIM "tenant", ISSO RETORNA NULL
}
```

### ⚠️ SE A LINHA QUE ADICIONA "tenant" AO MAP NÃO EXISTE:
**ADICIONE IMEDIATAMENTE:**
```java
public String generateToken(User user, String tenantId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("subject", user.getEmail());
    claims.put("userId", user.getId().toString());
    claims.put("tenant", tenantId);  // ← ADICIONAR ESSA LINHA
    claims.put("roles", user.getRoles().stream()
        .map(r -> r.getName())
        .collect(Collectors.toList()));
    
    return createToken(claims, user.getEmail());
}
```

---

## 4. 🔴 SubscriptionGuard.java - TENANTCONTEXT RETORNA NULL

**Arquivo:** `src/main/java/com/leadflow/backend/security/SubscriptionGuard.java`

### Linha 107-125: ONDE OCORRE O ERRO PRINCIPAL
```java
107 | private Vendor resolveVendorStrict() {
108 |
109 |     Authentication authentication =
110 |         SecurityContextHolder.getContext().getAuthentication();
111 |
112 |     if (authentication == null ||
113 |         !authentication.isAuthenticated() ||
114 |         authentication instanceof AnonymousAuthenticationToken) {
115 |         throw new AccessDeniedException("Authentication required");
116 |     }
117 |
118 |     String email = authentication.getName();
119 |
120 |     if (email == null || email.isBlank()) {
121 |         throw new AccessDeniedException("Invalid authentication principal");
122 |     }
123 |
124 |     String tenant = TenantContext.getTenant();  // ← LINE 124: PODE RETORNAR NULL AQUI!
125 |
126 |     if (tenant == null || tenant.isBlank()) {
127 |         log.error("TenantContext is NULL or empty for user={}", maskEmail(email));
128 |         throw new AccessDeniedException("Tenant context not resolved");  // ← ERRO AQUI
129 |     }
130 |
131 |     log.debug("Resolving vendor → user={}, tenant={}",
132 |         maskEmail(email), tenant);
133 |
134 |     return vendorRepository
135 |         .findByUserEmailAndTenantId(email, tenant)
136 |         .stream()
137 |         .findFirst()
138 |         .orElseThrow(() -> {
139 |             log.error("Vendor not found → user={}, tenant={}",
140 |                 maskEmail(email), tenant);
141 |             return new AccessDesiedException("Vendor not found for current tenant");  // ← OUTRO ERRO POSSÍVEL
142 |         });
143 | }
```

### O Problema
- Linha 124: `TenantContext.getTenant()` retorna NULL
- Linha 126-129: Testa se NULL e lança AccessDeniedException
- **CAUSA:** TenantFilter falhou (veja acima) e nunca chamou `TenantContext.setTenant()`

---

## 5. 🔴 LeadController.java - CHAMA enforceWriteAccess QUE FALHA

**Arquivo:** `src/main/java/com/leadflow/backend/controller/lead/LeadController.java`

### Linha 56-60: O INÍCIO DO ERRO
```java
56 | @PostMapping
57 | public ResponseEntity<LeadResponse> createLead(
58 |     @AuthenticationPrincipal UserDetails principal,
59 |     @Valid @RequestBody CreateLeadRequest request
60 | ) {
61 |
62 |     try {
63 |         enforceWriteAccess();  // ← LINE 63: CHAMA enforceWriteAccess()
                                       que usa SubscriptionGuard
                                       que getTenant() de TenantContext
                                       que pode ser NULL
```

### Linha 165-172: DENTRO DE enforceWriteAccess()
```java
165 | private void enforceWriteAccess() {
166 |
167 |     if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
168 |         log.warn("Write access denied due to insufficient subscription level");
169 |         throw new ResponseStatusException(
170 |             HttpStatus.FORBIDDEN,
171 |             "Subscription does not allow write operations"
172 |         );
173 |     }
174 | }
```

### A Sequência de Falha
```
LeadController.createLead() LINHA 56
    ↓
enforceWriteAccess() LINHA 63
    ↓
subscriptionGuard.resolveAccess() LINHA 167
    ↓
resolveVendorStrict() [SubscriptionGuard.java:107]
    ↓
TenantContext.getTenant() LINHA 124 [SubscriptionGuard.java]
    ↓
❌ RETORNA NULL
    ↓
💥 AccessDeniedException("Tenant context not resolved") LINHA 128
```

---

## 6. 🔴 UserController.java - @PreAuthorize EM TODA A CLASS

**Arquivo:** `src/main/java/com/leadflow/backend/controller/user/UserController.java`

### Linha 20-21: SOBRE-AUTORIZAÇÃO
```java
20 | @RestController
21 | @RequestMapping("/users")
22 | @PreAuthorize("hasRole('ADMIN')")  // ← LINE 22: REQUER ADMIN EM TODOS ENDPOINTS!
23 | public class UserController {
24 |
25 |     // ...
26 |
27 |     @GetMapping
28 |     public ResponseEntity<Page<UserResponse>> list(Pageable pageable) {
29 |         // ← TAMBÉM REQUER ADMIN
30 |     }
```

### O Problema
Se usuário não tem role ADMIN:
- Spring Security bloqueia em LINE 22
- Retorna 403 Forbidden
- Nem chega no controlador

---

## RESUMO: LINHAS CRÍTICAS

| Arquivo | Linha | Problema | Ação |
|---------|-------|----------|------|
| TenantFilter.java | 135 | tenantResolver.resolveTenant() retorna NULL | Verificar JWT |
| TenantFilter.java | 141 | return sem setTenant | Falha no JWT/Header |
| TenantResolver.java | 65 | extractTenantFromJwt retorna NULL | Verificar JwtService |
| TenantResolver.java | 90-93 | Lança 401 Unauthorized | Falta JWT claim "tenant" |
| **JwtService.java** | ??? | **NÃO ADICIONA CLAIM "tenant"** | **ADICIONAR CLAIM** |
| SubscriptionGuard.java | 124 | TenantContext.getTenant() = NULL | TenantFilter não setou |
| SubscriptionGuard.java | 128 | AccessDeniedException | Resultado final |
| LeadController.java | 63 | enforceWriteAccess() | Chama SubscriptionGuard |
| UserController.java | 22 | @PreAuthorize("hasRole('ADMIN')") | Requer role |

---

## 🔍 QUICK FIX CHECKLIST

- [ ] **1. PROCURE EM JwtService.java:**
  - Procure: `claims.put("tenant"`
  - Se NÃO encontra: **ADICIONE A LINHA**
  
- [ ] **2. VER O DATABASE:**
  - `SELECT * FROM vendors WHERE user_email = 'SEU-EMAIL' AND tenant_id = 'public';`
  - Se vazio: **INSIRA VENDOR**
  
- [ ] **3. TESTAR:**
  - Execute: `.\test-leads-all-FIXED.ps1`
  - Deve retornar: ✅ PASS RATE: 100%

---

## 🚨 SE AINDA FALHAR

**Ative logs DEBUG:**

Adicione em `application.yml`:
```yaml
logging:
  level:
    com.leadflow.backend.multitenancy: DEBUG
    com.leadflow.backend.security: DEBUG
    com.leadflow.backend.controller: DEBUG
```

Depois execute e procure por:
- `"Tenant resolved:"`
- `"TenantContext is NULL"`
- `"Vendor not found"`
- `"Subscription"`

E compartilhe os logs para análise.
