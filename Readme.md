# Rota — Rotating Savings Infrastructure on Nomba

> **"We didn't build another Ajo app. We built the rails every Ajo app can run on."**

Rota is a **multi-tenant REST API** that lets any platform — a church app, a cooperative tool, a community fintech — provision dedicated Nomba virtual accounts and run fraud-resistant rotating savings groups (Ajo/Esusu) without writing a single line of payment logic.

Built for the **Nomba x DevCareer Hackathon 2026** · Infrastructure Track · Dedicated Virtual Accounts

---

## The Problem

₦1 trillion+ moves through rotating savings groups (Ajo/Esusu) in Nigeria every year. It still runs on notebooks and trust. When someone collects their pot early and stops paying, there is no recourse — and this happens constantly.

Existing digital solutions digitized the bookkeeping but left the structural fraud risk untouched. And none of them are infrastructure — they are closed consumer apps. Every fintech, cooperative, or community platform that wants to offer group savings has to build the entire money machinery from scratch.

**That is the gap Rota fills.**

---

## What Rota Does

A client application makes one API call to create a savings group. Rota:

- Provisions a **dedicated Nomba virtual account** for every member
- Verifies each member's payout bank account via Nomba's bank lookup
- Locks a **trust-ordered rotation** — members with no payment history collect last, so the person most likely to default hasn't been paid yet
- Automatically **reconciles every contribution** via signed Nomba webhooks, with DB-level idempotency (no double-credits, ever)
- **Fires payouts automatically** to the correct member's pre-verified bank account when a cycle is fully funded — no manual intervention
- **Detects defaults** when a cycle's period ends with unpaid contributions, drops the member's trust score, and flags the group

Client apps never write payment code. They call Rota's API, get events back, and show their users what happened.

---

## Why This Is Infrastructure, Not Another App

| | AjoMoney / Alajo App | **Rota** |
|---|---|---|
| Who is the customer? | End savers | Developers / businesses |
| Can you build on top of it? | No — closed app | Yes — clean REST API |
| Multi-tenant? | No | Yes — API key per integrator |
| Fraud mitigation | Bookkeeping only | Trust-ordered rotation by design |
| Webhook events to clients? | No | Yes — integrators get real-time events |

---

## How Integration Works

**Step 1 — Register as an integrator (once):**
```bash
curl -X POST https://your-rota-url/api/v1/integrators/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Grace Chapel App"}'
```
Returns a `rota_test_...` API key. Store it. It is shown only once.

**Step 2 — Create a savings group:**
```bash
curl -X POST https://your-rota-url/api/v1/groups \
  -H "X-Api-Key: rota_test_..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tech Bros Weekly",
    "contributionAmount": 5000,
    "frequency": "WEEKLY"
  }'
```

**Step 3 — Add members:**
```bash
curl -X POST https://your-rota-url/api/v1/groups/{groupId}/members \
  -H "X-Api-Key: rota_test_..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Ada Okafor",
    "email": "ada@example.com",
    "kycTier": "TIER_2",
    "payoutAccountNumber": "0123456789",
    "payoutBankCode": "058"
  }'
```

**Step 4 — Provision virtual accounts + activate:**
```bash
curl -X POST https://your-rota-url/api/v1/groups/{groupId}/provision \
  -H "X-Api-Key: rota_test_..."

curl -X POST https://your-rota-url/api/v1/groups/{groupId}/activate \
  -H "X-Api-Key: rota_test_..."
```

**After activation — everything is automatic.** Members transfer money to their dedicated account numbers. Rota reconciles contributions via Nomba webhooks, fires payouts when cycles complete, and sends events to your app's webhook URL.

---

## Core Design Guarantees

**Custody-free.** Rota never holds funds and never accepts a free-form payout destination. The only valid payout target is the next member in the locked rotation, paid to their pre-verified bank account. Nomba's rails hold the money; Rota only orchestrates instructions.

**DB-level idempotency.** Unique database constraints on webhook event IDs, ledger transaction references, and (group, cycle) payout pairs. Two simultaneous duplicate webhook deliveries cannot double-credit anyone — enforced at the database, not just in application code.

**Trust-ordered rotation.** The rotation order is set by trust score before locking. Members with no history or poor history collect last — so whoever is most able to default hasn't been paid yet. The order locks permanently when the group activates.

**The ledger is the only source of truth.** All Nomba inflows auto-route into one parent wallet. Group isolation exists entirely in Rota's append-only double-entry ledger. A group can never pay out more than its own tracked balance.

**Honest about limits.** Rota cannot recover funds from someone who collected and walked away — no software can. What Rota does: make default structurally harder, detect it instantly, record it as a tracked debt, and ensure the defaulter's trust score follows them across every future group on the network.

---

## API Reference

Live interactive docs (try endpoints directly):
```
https://barrable-jesica-judgmental.ngrok-free.dev/swagger-ui.html
```

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/integrators/register` | Register and get an API key (public) |
| POST | `/api/v1/groups` | Create a savings group |
| GET | `/api/v1/groups` | List your groups |
| GET | `/api/v1/groups/{id}` | Get group details |
| POST | `/api/v1/groups/{id}/members` | Add a member |
| POST | `/api/v1/groups/{id}/provision` | Provision Nomba virtual accounts |
| POST | `/api/v1/groups/{id}/activate` | Lock rotation and start the cycle |
| GET | `/api/v1/groups/{id}/upcoming-dues` | See who owes, and when |
| POST | `/webhooks/nomba` | Nomba inbound webhook receiver (signature-verified) |

All endpoints except `/api/v1/integrators/register` and `/webhooks/nomba` require `X-Api-Key` header.

---

## Tech Stack

- **Java 17** + **Spring Boot 4.x**
- **Spring Data JPA** + **PostgreSQL**
- **Spring Security** (API key authentication)
- **Nomba Sandbox API** — Virtual Accounts, Transfers, Webhooks, Transactions
- **springdoc-openapi** — auto-generated Swagger UI

---

## Running Locally

**Prerequisites:** Java 17, PostgreSQL, a Nomba sandbox account

**1. Clone and configure:**
```bash
git clone https://github.com/DevGloriaa/Rota-Team-Aura
cd rota
```

Create a `.env` file (or set environment variables in your IDE run config):
```
DB_USER=postgres
DB_PASSWORD=your_postgres_password
NOMBA_ACCOUNT_ID=your_parent_account_id
NOMBA_CLIENT_ID=your_test_client_id
NOMBA_CLIENT_SECRET=your_test_private_key
NOMBA_SUB_ACCOUNT_ID=your_sub_account_id
NOMBA_WEBHOOK_SECRET=NombaHackathon2026
```

**2. Create the database:**
```sql
CREATE DATABASE ajo;
```

**3. Run:**
```bash
./mvnw spring-boot:run
```

Hibernate auto-creates all tables on first boot. App runs on `http://localhost:8080`.

**4. Expose webhook endpoint (for Nomba to reach you):**
```bash
ngrok http --domain=your-static-domain.ngrok-free.dev 8080
```

**5. Open Swagger UI:**
```
http://localhost:8080/swagger-ui.html
```

---

## Project Structure

```
src/main/java/com/aura/ajo/
├── config/          # Security, OpenAPI, Jackson, filter wiring
├── controller/      # REST endpoints
├── dto/             # Request/response shapes (flat package)
├── entity/          # JPA entities
├── exception/       # Global exception handler + domain exceptions
├── filter/          # ApiKeyAuthFilter (multi-tenancy)
├── repository/      # Spring Data JPA repositories
├── service/         # Service interfaces
├── serviceImpl/     # Business logic implementations
└── util/            # HashUtils (SHA-256 for API key hashing)
```

---

## Built By

**Team Aura** — Gloria & Treasure
Nomba x DevCareer Hackathon 2026
