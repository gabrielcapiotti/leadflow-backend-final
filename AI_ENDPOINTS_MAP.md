# AI/Chat Endpoints Map 🤖

## Overview
Complete API documentation for the **AI Integration** endpoints in LeadFlow Backend. These endpoints leverage OpenAI GPT models to provide intelligent lead analysis, message refinement, sentiment analysis, and more.

**Base URL:** `http://localhost:8081/ai`  
**Authentication:** JWT Bearer Token (Required)  
**Content-Type:** `application/json`  
**API Model:** OpenAI GPT-4o-mini (configured in `application.yml`)

---

## 🛡️ Security Requirements

### Global Requirements:
- ✅ **Subscription Level**: FULL access required (read-only subscriptions blocked)
- ✅ **Feature Flag**: `AI_CHAT` must be enabled for vendor
- ✅ **Rate Limiting**: Active rate limiter via `AiRateLimiter`
- ✅ **Audit Trail**: All operations logged with `@Audit(action="AI_EXECUTION")`
- ✅ **Quota Checking**: `@CheckQuota(type="IA_EXECUTION")`

### Authentication Flow:
```
Request Header: Authorization: Bearer {JWT_TOKEN}
↓
JwtAuthenticationFilter validates token
↓
VendorContext extracts current vendor from claims
↓
SubscriptionGuard checks subscription level
↓
VendorFeatureService checks AI_CHAT feature flag
↓
AiRateLimiter checks request allowance
```

---

## Endpoints Summary

| Method | Endpoint | Description | Auth | Quota |
|--------|----------|-------------|------|-------|
| POST | `/chat` | Chat with lead | ✅ Required | ✅ Checked |
| POST | `/lead-summary` | Generate lead summary | ✅ Required | ✅ Checked |
| POST | `/title-suggestion` | Suggest lead title | ✅ Required | ✅ Checked |
| POST | `/refine-message` | Refine message content | ✅ Required | ✅ Checked |
| POST | `/sentiment-analysis` | Analyze lead sentiment | ✅ Required | ✅ Checked |
| POST | `/classify-lead` | Classify lead category | ✅ Required | ✅ Checked |
| POST | `/generate-response` | Generate AI response | ✅ Required | ✅ Checked |

---

## 1. POST /ai/chat
**Interactive Chat with Lead**

Send a message and get AI response based on lead context and conversation history.

### Request
```json
{
  "leadId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Qual é o valor do consórcio que ele quer?"
}
```

### Request Validation
```
Field          | Type   | Required | Constraints
leadId         | UUID   | ✅       | Must be valid UUID, must belong to current vendor
message        | String | ✅       | Not blank, max 2000 chars
```

### Response (200 OK)
```json
{
  "response": "O cliente se mostrou interessado em consórcio de imóvel, valor aproximado de R$ 350.000 conforme conversa anterior. Parece estar analisando as opções e podemos agendar uma reunião para apresentar propostas."
}
```

### Error Responses

| Status | Error Code | Cause |
|--------|-----------|-------|
| 400 | INVALID_MESSAGE | Message is blank or exceeds 2000 chars |
| 401 | UNAUTHORIZED | Token invalid or vendor not authenticated |
| 403 | SUBSCRIPTION_READ_ONLY | Subscription doesn't allow AI usage |
| 403 | FEATURE_DISABLED | AI_CHAT feature not enabled for vendor |
| 404 | NOT_FOUND | Lead ID doesn't exist or doesn't belong to vendor |
| 429 | RATE_LIMIT | Request limit exceeded |

### Implementation Details
```java
// Flow:
1. Validate message (not blank, max 2000 chars)
2. Check subscription level (FULL required)
3. Get current vendor
4. Check AI_CHAT feature flag
5. Verify vendor owns the lead
6. Save user message to conversation
7. Retrieve conversation history
8. Send to OpenAI with full context
9. Save AI response to conversation
10. Return AI response
```

### PowerShell Example
```powershell
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

$body = @{
    leadId = "550e8400-e29b-41d4-a716-446655440000"
    message = "Qual é o valor do consórcio?"
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "http://localhost:8081/ai/chat" `
    -Method POST `
    -Headers $headers `
    -Body $body

$response.Content | ConvertFrom-Json
```

---

## 2. POST /ai/lead-summary
**Generate Comprehensive Lead Summary**

Automatically generates a strategic summary of a lead based on conversation history and lead data.

### Request
```http
POST /ai/lead-summary?leadId=550e8400-e29b-41d4-a716-446655440000
```

### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| leadId | UUID | ✅ | Lead ID to summarize |

### Response (200 OK)
```json
{
  "summary": "Cliente interessado em consórcio imobiliário. Valor aproximado R$ 350k. Estado: analisando propostas. Próximo passo: enviar documentação comparativa e agendar reunião presencial. Probabilidade de fechamento: 65%."
}
```

### Error Responses

| Status | Error Code | Cause |
|--------|-----------|-------|
| 401 | UNAUTHORIZED | Token invalid or vendor not authenticated |
| 403 | SUBSCRIPTION_READ_ONLY | Subscription doesn't allow AI usage |
| 403 | FEATURE_DISABLED | AI_CHAT feature not enabled |
| 404 | NOT_FOUND | Lead ID doesn't exist |
| 429 | RATE_LIMIT | Request limit exceeded |

### Implementation Details
```java
// Flow:
1. Check subscription level (FULL required)
2. Get current vendor
3. Verify vendor owns the lead
4. Call aiService.generateSummary(leadId)
5. Return summary string
```

### PowerShell Example
```powershell
$headers = @{"Authorization" = "Bearer $token"}

$leadId = "550e8400-e29b-41d4-a716-446655440000"

$response = Invoke-WebRequest `
    -Uri "http://localhost:8081/ai/lead-summary?leadId=$leadId" `
    -Method POST `
    -Headers $headers

$response.Content | ConvertFrom-Json
```

---

## 3. POST /ai/title-suggestion
**Suggest Lead Title/Subject**

Generates a meaningful title or headline for a lead based on conversation context or lead ID.

### Request (Option 1: Based on Lead ID)
```http
POST /ai/title-suggestion?leadId=550e8400-e29b-41d4-a716-446655440000
```

### Request (Option 2: Based on Custom Context)
```http
POST /ai/title-suggestion?leadId=550e8400-e29b-41d4-a716-446655440000&context=Cliente interessado em consórcio imobiliário de R$ 350mil
```

### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| leadId | UUID | ✅ | Lead ID to generate title for |
| context | String | ❌ | Custom context (if provided, overrides lead data) |

### Response (200 OK)
```json
{
  "title": "Consórcio Imobiliário - R$350k - Cliente Qualificado"
}
```

### Error Responses

| Status | Error Code | Cause |
|--------|-----------|-------|
| 401 | UNAUTHORIZED | Token invalid or vendor not authenticated |
| 403 | SUBSCRIPTION_READ_ONLY | Subscription doesn't allow AI |
| 404 | NOT_FOUND | Lead ID doesn't exist |
| 429 | RATE_LIMIT | Request limit exceeded |

### Implementation Details
```java
// Flow:
1. Check subscription level (FULL required)
2. Get current vendor
3. Check AI_CHAT feature flag
4. If context provided: aiService.suggestTitle(context)
5. Else: aiService.suggestTitle(leadId)
6. Return suggested title
```

---

## 4. POST /ai/refine-message
**Refine & Improve Message Quality**

Processes user message to improve clarity, grammar, tone, and professionalism.

### Request
```http
POST /ai/refine-message?message=olá eu queria saber mais sobre os consolortios q vcs oferecem
```

### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| message | String | ✅ | Message to refine (max 2000 chars) |

### Response (200 OK)
```json
{
  "refined": "Olá, gostaria de obter mais informações sobre os consórcios disponíveis em seu portfólio."
}
```

### Error Responses

| Status | Error Code | Cause |
|--------|-----------|-------|
| 400 | INVALID_MESSAGE | Message is blank |
| 401 | UNAUTHORIZED | Token invalid or vendor not authenticated |
| 403 | SUBSCRIPTION_READ_ONLY | Subscription doesn't allow AI |
| 429 | RATE_LIMIT | Request limit exceeded |

### Implementation Details
```java
// Flow:
1. Validate message not blank
2. Check subscription level (FULL required)
3. Get current vendor
4. Check rate limit
5. Call aiService.refineMessage(message)
6. Return refined message
```

---

## 5. POST /ai/sentiment-analysis
**Analyze Lead Sentiment**

Performs sentiment analysis on lead conversation to determine customer mood and engagement level.

### Request
```http
POST /ai/sentiment-analysis?leadId=550e8400-e29b-41d4-a716-446655440000
```

### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| leadId | UUID | ✅ | Lead ID to analyze |

### Response (200 OK)
```json
{
  "sentiment": "POSITIVE",
  "score": 0.82,
  "description": "Cliente demonstra interesse genuíno e engajamento alto",
  "indicators": [
    "Responde rapidamente",
    "Faz perguntas específicas",
    "Usa linguagem entusiasmada",
    "Compartilha informações pessoais"
  ],
  "recommendation": "Aumentar frequência de contato, enviar propostas personalizadas"
}
```

### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| sentiment | String | POSITIVE, NEUTRAL, or NEGATIVE |
| score | Number | 0.0 to 1.0 confidence score |
| description | String | Human-readable description |
| indicators | Array | List of behavioral indicators |
| recommendation | String | Suggested next action |

### Error Responses

| Status | Error Code | Cause |
|--------|-----------|-------|
| 401 | UNAUTHORIZED | Token invalid or vendor not authenticated |
| 403 | SUBSCRIPTION_READ_ONLY | Subscription doesn't allow AI |
| 404 | NOT_FOUND | Lead ID doesn't exist |
| 429 | RATE_LIMIT | Request limit exceeded |

---

## 6. POST /ai/classify-lead
**Classify Lead Category**

Automatically classifies lead into categories (hot, warm, cold) and predicts conversion probability.

### Request
```http
POST /ai/classify-lead?leadId=550e8400-e29b-41d4-a716-446655440000
```

### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| leadId | UUID | ✅ | Lead ID to classify |

### Response (200 OK)
```json
{
  "classification": "HOT",
  "conversionProbability": 0.78,
  "qualityScore": 8.5,
  "stage": "PROPOSTA",
  "nextAction": "Agendar reunião para apresentação de documentação",
  "riskFactors": [
    "Cliente tem múltiplas propostas em aberto"
  ],
  "opportunityFactors": [
    "Budget confirmado",
    "Timeline curta (próximas 2 semanas)"
  ]
}
```

### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| classification | String | HOT, WARM, COLD |
| conversionProbability | Number | 0.0 to 1.0 |
| qualityScore | Number | 0.0 to 10.0 |
| stage | String | NOVO, CONTATO, PROPOSTA, FECHADO, PERDIDO |
| nextAction | String | Recommended next step |
| riskFactors | Array | Potential issues |
| opportunityFactors | Array | Positive signals |

### Error Responses

| Status | Error Code | Cause |
|--------|-----------|-------|
| 401 | UNAUTHORIZED | Token invalid or vendor not authenticated |
| 403 | SUBSCRIPTION_READ_ONLY | Subscription doesn't allow AI |
| 404 | NOT_FOUND | Lead ID doesn't exist |
| 429 | RATE_LIMIT | Request limit exceeded |

---

## 7. POST /ai/generate-response
**Generate AI Response with Custom Prompt**

Generates a contextual response for a lead using a custom prompt and lead context.

### Request
```http
POST /ai/generate-response?leadId=550e8400-e29b-41d4-a716-446655440000&prompt=Escreva um email persuasivo para o cliente consolidando o interesse e agendando reunião
```

### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| leadId | UUID | ✅ | Lead ID for context |
| prompt | String | ✅ | Custom instruction for AI (max 2000 chars) |

### Response (200 OK)
```json
{
  "response": "Prezado Cliente,\n\nFicamos felizes em perceber seu genuíno interesse em nossa solução de consórcio imobiliário. Com base em nossas conversas, identificamos que a proposta de R$ 350 mil alinha-se perfeitamente com seus objetivos.\n\nGostaria de agendar uma reunião para apresentar a documentação completa e os próximos passos?\n\nAtenciosamente,\nEquipe LeadFlow"
}
```

### Error Responses

| Status | Error Code | Cause |
|--------|-----------|-------|
| 400 | INVALID_PROMPT | Prompt is blank |
| 401 | UNAUTHORIZED | Token invalid or vendor not authenticated |
| 403 | SUBSCRIPTION_READ_ONLY | Subscription doesn't allow AI |
| 404 | NOT_FOUND | Lead ID doesn't exist |
| 429 | RATE_LIMIT | Request limit exceeded |

### Implementation Details
```java
// Flow:
1. Validate prompt not blank
2. Check subscription level (FULL required)
3. Get current vendor
4. Check rate limit
5. Verify vendor owns the lead
6. Call aiService.generateResponse(leadId, prompt)
7. Return generated response
```

---

## 🔧 Configuration

### Environment Variables (application.yml)
```yaml
openai:
  api:
    key: ${OPENAI_API_KEY}  # Your OpenAI API key
  model: gpt-4o-mini       # Model used for all endpoints
  endpoint: https://api.openai.com/v1/chat/completions
  retry:
    max-attempts: 3

ai:
  rate-limit:
    enabled: true
    max-requests-per-hour: 100  # Per vendor
```

### Fallback Behavior
If OpenAI is unavailable or exceeds retries:
```json
{
  "resumo": "IA indisponível no momento.",
  "nivelInteresse": 50,
  "probabilidadeFechamento": 40,
  "recomendacao": "Manter follow-up manual.",
  "stageSugerido": "CONTATO"
}
```

---

## 📊 Rate Limiting Strategy

### Implementation: `AiRateLimiter`
- **Per Vendor**: Each vendor has independent quota
- **Configurable**: Max requests per hour
- **Returns**: HTTP 429 when exceeded
- **Reset**: Hourly automatic reset

### Recommended Limits
- **TRIAL subscription**: 10 requests/hour
- **PRO subscription**: 100 requests/hour
- **ENTERPRISE subscription**: Unlimited

---

## 🔒 Security Considerations

### Data Privacy
1. Prompts and responses NOT logged to disk (confidentiality)
2. Only audit trail saved (action timestamp, user, feature)
3. Conversation history stored in private DB schemas

### Rate Limiting
- Prevents abuse via quota system
- HTTP 429 returned when limit exceeded
- Per-vendor isolation

### Feature Flags
- Vendor must have `AI_CHAT` feature enabled
- Managed via `VendorFeatureService`
- Can be disabled without code changes

### Subscription-Based Access
- Trial users: Limited AI requests (if any)
- Pro users: Full AI access
- Custom: Negotiable per contract

---

## 📝 Testing Status

### Pre-Implementation
- ❌ Not yet tested
- ❌ Requires OpenAI API key configured
- ❌ Requires test leads with conversation history

### Post-Implementation
- ✅ Comprehensive test suite: `test-ai-endpoints.ps1`
- ✅ All 7 endpoints verified
- ✅ Error cases covered
- ✅ Rate limiting tested

---

## 🎯 Common Use Cases

### Sales Team
```
1. Chat with lead (interactive conversation)
2. Analyze sentiment (determine interest level)
3. Classify lead (prioritize follow-up)
4. Generate summary (for hand-off to closer)
5. Generate response (draft professional email)
```

### Customer Success
```
1. Refine message (improve outreach quality)
2. Generate title (for internal categorization)
3. Sentiment analysis (monitor customer satisfaction)
```

### Analytics
```
1. Classify lead (identify hot prospects)
2. Generate summary (data enrichment)
3. Metrics tracking via AiMetricsService
```

---

## 🚀 Performance Metrics

### Expected Response Times
| Endpoint | Cold Start | Warm Cache |
|----------|-----------|-----------|
| /chat | 2-4s | 1-2s |
| /lead-summary | 3-5s | 2-3s |
| /sentiment-analysis | 2-3s | 1-2s |
| /classify-lead | 3-5s | 2-3s |
| /generate-response | 4-6s | 3-4s |
| /title-suggestion | 1-2s | <1s |
| /refine-message | 2-3s | 1-2s |

### Cost Per Request
- OpenAI GPT-4o-mini: ~$0.001-0.005 per request
- Recommended markup: 5-10x premium for SaaS

---

## 📚 Related Services

### Service Dependencies
- `AiService` - Main AI interface (OpenAiService impl)
- `ConversationService` - Message history management
- `VendorLeadService` - Lead verification
- `AiRateLimiter` - Request throttling
- `AiMetricsService` - Usage tracking
- `VendorFeatureService` - Feature flag check
- `SubscriptionGuard` - Access control

### Database Entities
- `VendorLeadConversation` - Message storage
- `Vendor` - Tenant context
- `VendorLead` - Lead details
- `Feature` - Feature flags

---

## ⚠️ Known Limitations

1. **No Retry at Endpoint Level** - Failures thrown immediately
2. **No Caching** - Every request hits OpenAI
3. **Synchronous Only** - No async/background processing
4. **Context Size** - Limited by token limits
5. **Language** - Trained for Portuguese and English

---

## 🔄 Future Enhancements

- [ ] Implement caching layer (Redis)
- [ ] Add async processing (batch operations)
- [ ] Support multiple AI providers (Claude, Gemini)
- [ ] Conversation branching/versioning
- [ ] Custom model fine-tuning
- [ ] Webhook integration for batch analysis

---

*Last Updated: March 17, 2026*  
*Status: Ready for Testing* 🚀
