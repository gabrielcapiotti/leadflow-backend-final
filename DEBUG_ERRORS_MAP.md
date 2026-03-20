# 🔍 Mapeamento Detalhado dos Erros

## Resumo dos 11 Testes
- ✅ **1-6, 11**: Passing (Health, Register, Login, Me, Create Lead, List Leads, Delete Lead)
- ❌ **7-10**: Failing (Create Vendor Lead, Vendor Leads, Update Stage, Refresh Token)

---

## 🔴 ERRO #1: Create Vendor Lead (Passo 7) - HTTP 400

### Endpoint
`POST /vendor-leads/leads`

### Request Body (ATUAL - ERRADO ❌)
```json
{
  "nomeCompleto": "Maria",
  "whatsapp": "999999999",
  "tipoConsorcio": "VEICULO",
  "valorCredito": "100000",
  "urgencia": "medio"   ⚠️ INVÁLIDO
}
```

### Raiz do Erro ✅ IDENTIFICADA
No `CreateLeadRequest.java`:
```java
@Pattern(regexp = "quero_fechar|analisando|pesquisando")
private String urgencia;
```

Valores aceitos: `quero_fechar`, `analisando`, `pesquisando`
Valor enviado: `medio` ❌

### Solução
Alterar no teste para `urgencia = "quero_fechar"` ✅ **JÁ FEITO**

---

## 🔴 ERRO #2: Vendor Leads (Passo 8) - HTTP 401

### Endpoint
`GET /vendor-leads?page=0&size=10`

### Request
```
Authorization: Bearer $AccessToken ✅ (correto)
X-Tenant-Id: public ✅ (correto)
```

### Raiz do Erro - POSSIBILIDADES:
1. **Token expirou** entre passo 3 (login) e passo 8 (vendor leads)?
   - Improvável em 1.4 segundos, mas possível se JWT tem TTL muito curto
   
2. **VendorLeadController faz `ensureVendorExists()`** que pode estar gerando exceção
   - Se `ensureVendorExists()` falha silenciosamente, pode retornar 401

3. **SubscriptionGuard.assertActive()** pode estar rejeitando
   - Vendedor pode não ter subscription ativa

### Código do Controller
```java
@GetMapping
public ResponseEntity<Page<VendorLead>> list(Pageable pageable) {
    subscriptionGuard.assertActive();  // ⚠️ AQUI pode estar o 401
    ensureVendorExists();               // ⚠️ OU AQUI
    return ResponseEntity.ok(service.listForCurrentVendor(pageable));
}
```

---

## 🔴 ERRO #3: Update Stage (Passo 9) - HTTP 400

### Endpoint
`PUT /vendor-leads/{vendorLeadId}/stage`

### Request Body
```json
{
  "stage": "QUALIFIED"
}
```

### Raiz do Erro - POSSIBILIDADES:
1. **$VendorLeadId é null ou inválido**
   - Se passo 7 falhou (400), $VendorLeadId nunca foi setado
   - Vai enviar PUT com ID null

2. **UpdateStageRequest.getStage() retorna null**
   - Validação falha em UpdateStageRequest

### Código
```powershell
$VendorLeadId = $r.Data.id  # ← Se passo 7 falha, $VendorLeadId = $null
```

```java
@PutMapping("/{id}/stage")
public ResponseEntity<?> updateStage(
        @PathVariable UUID id,  // ← Pode receber null UUID
        @RequestBody UpdateStageRequest request
) {
```

---

## 🔴 ERRO #4: Refresh Token (Passo 10) - HTTP 401

### Endpoint ERRADO ❌
Teste está usando: `/auth/refresh-token`

### Endpoint CORRETO ✅
Deve ser: `/auth/refresh`

### Request Body (ATUAL)
```json
{
  "refreshToken": "xxx"
}
```

### Código do Teste (Passo 10)
```powershell
Write-Step 10 "Refresh Token"
$r = Invoke-ApiRequest "POST" "/auth/refresh-token" @{  # ⚠️ ERRADO URL
    refreshToken = $RefreshToken                        # ✓ CORRETO campo
}
Test-Step "Refresh Token" $r
```

### Raiz do Erro ✅ IDENTIFICADA
- URL está **ERRADA**: `/auth/refresh-token` vs `/auth/refresh`
- Endpoint correto retorna 404 → Teste interpreta como 401

---

## 📋 Resumo dos Fixes Necessários

| # | Erro | Raiz | Fix | Status |
|---|------|------|-----|--------|
| 7 | 400 | `urgencia = "medio"` inválido | Mudar para `"quero_fechar"` | ✅ FEITO |
| 8 | 401 | Possivelmente SubscriptionGuard ou Vendor missing | Investigar logs | ⏳ PENDENTE |
| 9 | 400 | $VendorLeadId é null (dependência de passo 7) | Fix passo 7 → Fix passo 9 | ⏳ DEPENDENTE |
| 10 | 401 | URL errada: `/auth/refresh-token` vs `/auth/refresh` | Corrigir URL | ⏳ PENDENTE |

---

## 🧪 Próximos Passos

1. ✅ **Passo 7 FIX**: Já corrigido urgencia = "quero_fechar"
2. **Executar teste novamente** para ver novo status
3. **Passo 10 FIX**: Mudar URL para `/auth/refresh`
4. **Investigar passo 8**: Se ainda falhar, ver logs do servidor para entender 401
