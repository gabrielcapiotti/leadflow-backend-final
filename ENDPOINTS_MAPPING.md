# 📋 Mapeamento de Endpoints - LeadFlow

## 🟢 LEADS Endpoints (Corretos no Código)

### 1. CREATE LEAD
- **Endpoint Correto:** `POST /api/leads`
- **Auth Required:** Sim (Bearer Token)
- **Tenant Header:** Sim (X-Tenant-Id)
- **Request Body:**
  ```json
  {
    "name": "string",
    "email": "string",
    "phone": "string"
  }
  ```
- **Response:** 201 Created + LeadResponse

### 2. LIST LEADS
- **Endpoint Correto:** `GET /api/leads`
- **Auth Required:** Sim
- **Tenant Header:** Sim
- **Response:** 200 + List[LeadResponse]

### 3. GET LEAD BY ID
- **Endpoint Correto:** `GET /api/leads/{id}`
- **Auth Required:** Sim
- **Tenant Header:** Sim
- **Response:** 200 + LeadResponse

### 4. UPDATE LEAD STATUS
- **Endpoint Correto:** `PATCH /api/leads/{id}/status`
- **Auth Required:** Sim
- **Tenant Header:** Sim
- **Query Params:** `status=NEW|CONTACTED|QUALIFIED|CLOSED|LOST`
- **Response:** 200 + LeadResponse

### 5. DELETE LEAD
- **Endpoint Correto:** `DELETE /api/leads/{id}`
- **Auth Required:** Sim
- **Tenant Header:** Sim
- **Response:** 204 No Content

---

## 🟡 VENDOR LEADS Endpoints

### 1. CREATE VENDOR LEAD
- **Endpoint Correto:** `POST /api/vendor-leads/leads`
- **Auth Required:** Sim
- **Tenant Header:** Sim
- **Request Body:** (same as Lead)
- **Response:** 200 + VendorLead

### 2. LIST VENDOR LEADS
- **Endpoint Correto:** `GET /api/vendor-leads`
- **Auth Required:** Sim
- **Tenant Header:** Sim
- **Pageable:** Sim (page, size)
- **Response:** 200 + Page[VendorLead]

### 3. UPDATE VENDOR LEAD STAGE
- **Endpoint Correto:** `PUT /api/vendor-leads/{id}/stage`
- **Auth Required:** Sim
- **Tenant Header:** Sim
- **Request Body:**
  ```json
  {
    "stage": "string"
  }
  ```
- **Response:** 200 + VendorLead

### 4. GET VENDOR LEAD METRICS
- **Endpoint Correto:** `GET /api/vendor-leads/metrics`
- **Auth Required:** Sim
- **Response:** 200 + VendorLeadMetricsResponse

### 5. GET VENDOR LEAD RANKING
- **Endpoint Correto:** `GET /api/vendor-leads/ranking`
- **Auth Required:** Sim
- **Response:** 200 + List[VendorLead]

### 6. ASSIGN OWNER
- **Endpoint Correto:** `PUT /api/vendor-leads/{id}/owner`
- **Auth Required:** Sim
- **Response:** 200 + VendorLead

### 7. GET STAGE TIME METRICS
- **Endpoint Correto:** `GET /api/vendor-leads/metrics/stage-time`
- **Auth Required:** Sim
- **Response:** 200 + StageTimeMetricsResponse

### 8. GET CONVERSION METRICS
- **Endpoint Correto:** `GET /api/vendor-leads/metrics/conversion`
- **Auth Required:** Sim
- **Response:** 200 + StageConversionResponse

### 9. GET VENDOR LEAD CONVERSATION
- **Endpoint Correto:** `GET /api/vendor-leads/{id}/conversation`
- **Auth Required:** Sim
- **Response:** 200 + List[VendorLeadConversation]

### 10. GET VENDOR LEAD ALERTS
- **Endpoint Correto:** `GET /api/vendor-leads/{id}/alerts`
- **Auth Required:** Sim
- **Response:** 200 + List[Alerts]

### 11. UPDATE VENDOR LEAD RESUMO
- **Endpoint Correto:** `PUT /api/vendor-leads/{id}/resumo`
- **Auth Required:** Sim
- **Response:** 200 + string (resumo)

---

## 🔴 PROBLEMAS NO SCRIPT test-leads-all.ps1

### Test 5 - Create Lead
**ERRADO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/lead" ...
# ❌ Rota: /api/lead (SINGULAR)
# ❌ Campos: nomeCompleto, whatsapp, tipoConsorcio, valorCredito, urgencia
```

**CORRETO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/leads" ...
# ✅ Rota: /api/leads (PLURAL)
# ✅ Corpo:
@{
    name = "John Doe"
    email = "john@test.com"
    phone = "+5511999999999"
}
```

### Test 6 - Get Lead by ID
**ERRADO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/lead/$leadId" ...
```
**CORRETO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/leads/$leadId" ...
```

### Test 7 - Update Lead
**ERRADO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/lead/$leadId" -Method Put ...
# ❌ Método: PUT
# ❌ Rota: /api/lead (SINGULAR)
```
**CORRETO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/leads/$leadId/status?status=CONTACTED" -Method Patch ...
# ✅ Método: PATCH
# ✅ Rota: /api/leads/{id}/status
```

### Test 8 - List Leads
**ERRADO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/lead?page=0&size=10" ...
```
**CORRETO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/leads" ...
```

### Test 9 - Delete Lead
**ERRADO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/lead/$leadId" -Method Delete ...
```
**CORRETO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/leads/$leadId" -Method Delete ...
```

### Test 10 - Create Vendor Lead
**ERRADO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/vendor-leads/leads" ...
# ❌ Falta /api/
```
**CORRETO:**
```powershell
$response = Invoke-WebRequest -Uri "$BaseUrl/api/vendor-leads/leads" ...
# ✅ Com /api/
```

---

## 📊 Resumo

| # | Endpoint | Método | URL no Código | URL Real | Status |
|---|----------|--------|---------------|----------|--------|
| 5 | Create Lead | POST | `/api/lead` | `/api/leads` | ❌ |
| 6 | Get Lead | GET | `/api/lead/{id}` | `/api/leads/{id}` | ❌ |
| 7 | Update Lead | PUT | `/api/lead/{id}` | `/api/leads/{id}/status` | ❌ |
| 8 | List Leads | GET | `/api/lead?page=0&size=10` | `/api/leads` | ❌ |
| 9 | Delete Lead | DELETE | `/api/lead/{id}` | `/api/leads/{id}` | ❌ |
| 10 | Create VendorLead | POST | `/vendor-leads/leads` | `/api/vendor-leads/leads` | ❌ |

**TOTAL: 6/6 endpoints de lead/vendor-lead ERRADOS no script!**
