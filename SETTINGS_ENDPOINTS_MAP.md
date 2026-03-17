# Settings Endpoints Map 🔧

## Overview
Complete API documentation for the **Settings Management** endpoints in LeadFlow Backend. These endpoints allow users to manage their application settings including vendor name, WhatsApp contact, company branding, and welcome messages.

**Base URL:** `http://localhost:8081/settings`  
**Authentication:** JWT Bearer Token (Required)  
**Content-Type:** `application/json`

---

## Endpoints Summary

| Method | Endpoint | Description | Auth | Role |
|--------|----------|-------------|------|------|
| GET | `/settings` | Get current user settings | ✅ Required | User |
| GET | `/settings/{id}` | Get settings by ID | ✅ Required | Admin |
| PUT | `/settings` | Update user settings | ✅ Required | User |
| DELETE | `/settings` | Delete user settings (soft delete) | ✅ Required | User |

---

## 1. GET /settings
**Get Current User Settings**

Retrieves the settings associated with the authenticated user's account.

### Request
```http
GET /settings HTTP/1.1
Host: localhost:8081
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### cURL Example
```bash
curl -X GET http://localhost:8081/settings \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

### PowerShell Example
```powershell
$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
$headers = @{"Authorization" = "Bearer $token"; "Content-Type" = "application/json"}

Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
  -Method GET `
  -Headers $headers | Select-Object -ExpandProperty Content | ConvertFrom-Json
```

### Response (200 OK)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "vendorName": "LeadFlow Inc",
  "whatsapp": "5511999999999",
  "companyName": "LeadFlow Solutions",
  "logo": "https://logo.example.com/leadflow.png",
  "welcomeMessage": "Welcome to LeadFlow! We're happy to help you manage your leads."
}
```

### Status Codes
| Code | Description |
|------|-------------|
| 200 | Settings retrieved successfully |
| 401 | Unauthorized - Invalid or missing JWT token |
| 404 | Settings not found for user |

---

## 2. GET /settings/{id}
**Get Settings by ID (Admin)**

Retrieves settings by a specific ID. This endpoint is primarily used for admin purposes.

### Request
```http
GET /settings/{id} HTTP/1.1
Host: localhost:8081
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| id | UUID | The ID of the settings to retrieve |

### cURL Example
```bash
curl -X GET http://localhost:8081/settings/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

### PowerShell Example
```powershell
$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
$settingId = "550e8400-e29b-41d4-a716-446655440000"
$headers = @{"Authorization" = "Bearer $token"; "Content-Type" = "application/json"}

Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$settingId" `
  -Method GET `
  -Headers $headers | Select-Object -ExpandProperty Content | ConvertFrom-Json
```

### Response (200 OK)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "vendorName": "LeadFlow Inc",
  "whatsapp": "5511999999999",
  "companyName": "LeadFlow Solutions",
  "logo": "https://logo.example.com/leadflow.png",
  "welcomeMessage": "Welcome to LeadFlow! We're happy to help you manage your leads."
}
```

### Status Codes
| Code | Description |
|------|-------------|
| 200 | Settings retrieved successfully |
| 401 | Unauthorized - Invalid or missing JWT token |
| 404 | Settings not found with provided ID |

---

## 3. PUT /settings
**Update User Settings**

Creates or updates the settings for the authenticated user.

### Request
```http
PUT /settings HTTP/1.1
Host: localhost:8081
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "vendorName": "LeadFlow Inc",
  "whatsapp": "5511999999999",
  "companyName": "LeadFlow Solutions",
  "logo": "https://logo.example.com/leadflow.png",
  "welcomeMessage": "Welcome to LeadFlow! We're happy to help you manage your leads."
}
```

### Request Body Parameters
| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| vendorName | String | ✅ Yes | Max 100 chars | Name of the vendor/business |
| whatsapp | String | ✅ Yes | Max 15 chars | WhatsApp contact number |
| companyName | String | ❌ No | Max 100 chars | Name of the company |
| logo | String | ❌ No | URL format | Logo image URL |
| welcomeMessage | String | ❌ No | Max 500 chars | Welcome message for customers |

### cURL Example
```bash
curl -X PUT http://localhost:8081/settings \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "vendorName": "LeadFlow Inc",
    "whatsapp": "5511999999999",
    "companyName": "LeadFlow Solutions",
    "logo": "https://logo.example.com/leadflow.png",
    "welcomeMessage": "Welcome to LeadFlow!"
  }'
```

### PowerShell Example
```powershell
$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
$headers = @{"Authorization" = "Bearer $token"; "Content-Type" = "application/json"}

$body = @{
    vendorName = "LeadFlow Inc"
    whatsapp = "5511999999999"
    companyName = "LeadFlow Solutions"
    logo = "https://logo.example.com/leadflow.png"
    welcomeMessage = "Welcome to LeadFlow!"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
  -Method PUT `
  -Headers $headers `
  -Body $body | Select-Object -ExpandProperty Content | ConvertFrom-Json
```

### Response (200 OK)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "vendorName": "LeadFlow Inc",
  "whatsapp": "5511999999999",
  "companyName": "LeadFlow Solutions",
  "logo": "https://logo.example.com/leadflow.png",
  "welcomeMessage": "Welcome to LeadFlow!"
}
```

### Status Codes
| Code | Description |
|------|-------------|
| 200 | Settings created/updated successfully |
| 400 | Bad request - Validation error (invalid field values) |
| 401 | Unauthorized - Invalid or missing JWT token |
| 409 | Conflict - Brute force protection active |

### Validation Errors (400)
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-03-17T13:45:30Z",
  "details": {
    "vendorName": "Vendor name é obrigatório",
    "whatsapp": "WhatsApp é obrigatório"
  }
}
```

---

## 4. DELETE /settings
**Delete User Settings (Soft Delete)**

Soft-deletes the settings for the authenticated user. The data is marked as deleted but remains in the database.

### Request
```http
DELETE /settings HTTP/1.1
Host: localhost:8081
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### cURL Example
```bash
curl -X DELETE http://localhost:8081/settings \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

### PowerShell Example
```powershell
$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
$headers = @{"Authorization" = "Bearer $token"; "Content-Type" = "application/json"}

Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
  -Method DELETE `
  -Headers $headers
```

### Response (204 No Content)
```
(Empty body - HTTP 204)
```

### Status Codes
| Code | Description |
|------|-------------|
| 204 | Settings deleted successfully |
| 401 | Unauthorized - Invalid or missing JWT token |
| 404 | Settings not found |

---

## Authentication Flow

All Settings endpoints require JWT Bearer Token authentication:

1. **Obtain Token:** Login via `POST /auth/login`
   ```bash
   curl -X POST http://localhost:8081/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"user@example.com","password":"password123"}'
   ```

2. **Use Token:** Include in Authorization header
   ```bash
   Authorization: Bearer <accessToken>
   ```

3. **Token Expiration:** Handle 401 responses and refresh token if needed

---

## Security Considerations

- ✅ **Authentication Required:** All endpoints protected with JWT
- ✅ **User Isolation:** Users can only view/modify their own settings
- ✅ **Soft Delete:** Settings aren't permanently removed
- ✅ **Validation:** Input validation on all fields
- ✅ **Role-Based:** Admin can access any settings by ID
- 🔒 **Brute Force Protection:** 5 failed attempts = 5-minute lockout

---

## Error Handling

### Common Error Responses

**401 Unauthorized**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "User not authenticated",
  "timestamp": "2026-03-17T13:45:30Z"
}
```

**404 Not Found**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Settings not found",
  "timestamp": "2026-03-17T13:45:30Z"
}
```

**400 Bad Request**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-03-17T13:45:30Z",
  "details": {
    "vendorName": "must not be blank",
    "whatsapp": "must not be blank"
  }
}
```

**409 Conflict (Brute Force)**
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Too many failed attempts. Try again later.",
  "timestamp": "2026-03-17T13:45:30Z"
}
```

---

## Testing Checklist

- [ ] GET `/api/settings` - Retrieve user settings
- [ ] GET `/api/settings/{id}` - Retrieve by specific ID
- [ ] PUT `/api/settings` - Create/Update settings (all fields)
- [ ] PUT `/api/settings` - Update partial fields
- [ ] PUT `/api/settings` - Validation errors (missing required fields)
- [ ] DELETE `/api/settings` - Soft delete user settings
- [ ] Verify JWT token expiration handling
- [ ] Verify user isolation (cannot access other users' settings)
- [ ] Test with missing/invalid authentication header
- [ ] Test brute force protection (multiple failed requests)

---

## Implementation Notes

- **Tenant Isolation:** Settings are scoped per user and tenant
- **Schema:** Uses multi-tenant schema architecture
- **Logging:** Operations logged via SecurityAuditService
- **Mapper:** SettingMapper converts Entity ↔ Response DTOs
- **Validation:** Jakarta Validation annotations on request DTOs
- **Database:** Soft delete uses deleted_at timestamp

---

**Last Updated:** March 17, 2026  
**Version:** 1.0.0
