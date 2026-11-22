[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=DevSnobo_rate-payment-service-case-study&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=DevSnobo_rate-payment-service-case-study)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=DevSnobo_rate-payment-service-case-study&metric=coverage)](https://sonarcloud.io/summary/new_code?id=DevSnobo_rate-payment-service-case-study)

# rate-payment-service-case-study

A Spring Boot reference implementation of a small rate payment service. A real client uses it to
turn a claim into one payment plan, allocate incoming payments to installments, settle unused
credit with refunds, and read the current plan status.

This repository is a job-interview case study. The rules are deliberate and tested, but the
runtime is a demo runtime. The sections below describe how an integration uses the service and
where the production boundaries are.

## What the service does

- Seeds one claim for a demo or test.
- Creates one payment plan for a claim, either by installment count or by monthly amount.
- Allocates payments to installments, oldest due date first.
- Keeps an append-only payment and allocation ledger.
- Records partial payments, overpayments and unallocated credit.
- Refunds credit from a payment without changing the installments.
- Computes `ACTIVE`, `OVERDUE` or `COMPLETED` on every read.
- Returns RFC 9457 `ProblemDetail` errors.

## What it does not do

- No upstream claim system. The claim is local.
- No claim list, claim update or claim deletion.
- No customer or debtor aggregate.
- No second plan for a claim and no plan history.
- No quote or preview endpoint for comparing plan offers before accepting one.
- No payment reversal or chargeback of allocated money.
- No idempotent replay, no pagination and no multi-currency.
- No authentication, authorisation or production database.

## Quick start

Requirements: Java 21. The Maven wrapper is included.

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`. It uses an H2 in-memory database, so every start
begins with an empty schema. The demo credentials are `sa` / `password`.

Run the tests and coverage report:

```bash
./mvnw -B -ntp clean verify -Pcoverage
```

## Integration walkthrough

The examples use a `500.00` claim. Dates are fixed for readability. When you run them, use a
`paidOn` or `refundedOn` that is today or in the past, and a `firstDueDate` that is today or in
the future.

### 1. Seed a claim

```bash
curl -i -X POST http://localhost:8080/api/claims \
  -H 'Content-Type: application/json' \
  -d '{
    "claimNumber": 4711,
    "totalAmount": 500.00,
    "debtorName": "Ada Lovelace",
    "createdOn": "2026-11-21"
  }'
```

The response is `201 Created`. The claim number is returned zero-padded:

```json
{
  "claimNumber": "0000004711",
  "totalAmount": 500.00,
  "debtorName": "Ada Lovelace",
  "createdOn": "2026-11-21"
}
```

`POST /api/claims` is a demo seed endpoint. It is not full claim CRUD.

### 2. Create the plan

By monthly amount:

```bash
curl -i -X POST http://localhost:8080/api/claims/4711/payment-plan \
  -H 'Content-Type: application/json' \
  -d '{
    "installmentAmount": 80.00,
    "firstDueDate": "2026-12-01"
  }'
```

This creates six installments of `80.00` and one finishing installment of `20.00`. The response is
`201 Created` with `Location: /api/claims/0000004711/payment-plan`.

By count instead:

```json
{
  "installmentCount": 9,
  "firstDueDate": "2026-12-01"
}
```

This creates eight installments of `55.55` and a last installment of `55.60`.

The plan is created immediately. A second create for the same claim returns `409`.

### 3. Read the plan

```bash
curl -s http://localhost:8080/api/claims/0000004711/payment-plan
```

The response contains the claim total, paid amount, outstanding balance, credit, refunded amount,
computed status and ordered installments:

```json
{
  "claimNumber": "0000004711",
  "totalClaimAmount": 500.00,
  "amountPaid": 0.00,
  "outstandingBalance": 500.00,
  "creditAmount": 0.00,
  "refundedAmount": 0.00,
  "status": "ACTIVE",
  "installments": [
    {"installmentNumber": 1, "scheduledAmount": 80.00, "amountPaid": 0.00, "dueDate": "2026-12-01"},
    {"installmentNumber": 2, "scheduledAmount": 80.00, "amountPaid": 0.00, "dueDate": "2027-01-01"}
  ]
}
```

The example is abridged. A real response contains every installment.

### 4. Record a payment

```bash
curl -i -X POST http://localhost:8080/api/claims/4711/payment-plan/payments \
  -H 'Content-Type: application/json' \
  -d '{
    "amount": 550.00,
    "paidOn": "2026-11-21",
    "reference": "bank-transfer-123"
  }'
```

The service allocates `500.00` across the installments, completes the plan and keeps `50.00` as
unallocated credit:

```json
{
  "payment": {
    "paymentId": "6c8c6d30-6d9b-4e5e-9f2b-3f4c5d6e7f80",
    "amount": 550.00,
    "allocatedAmount": 500.00,
    "refundedAmount": 0.00,
    "unallocatedAmount": 50.00,
    "allocations": ["..."],
    "refunds": []
  },
  "paymentPlan": {
    "claimNumber": "0000004711",
    "totalClaimAmount": 500.00,
    "amountPaid": 500.00,
    "outstandingBalance": 0.00,
    "creditAmount": 50.00,
    "refundedAmount": 0.00,
    "status": "COMPLETED",
    "installments": ["..."]
  }
}
```

Copy the `paymentId` from the response for the refund step.

### 5. Refund part of the credit

```bash
PAYMENT_ID="6c8c6d30-6d9b-4e5e-9f2b-3f4c5d6e7f80"

curl -i -X POST \
  "http://localhost:8080/api/claims/4711/payment-plan/payments/${PAYMENT_ID}/refunds" \
  -H 'Content-Type: application/json' \
  -d '{
    "amount": 20.00,
    "refundedOn": "2026-11-21",
    "reference": "refund-123"
  }'
```

The plan stays `COMPLETED`. The installments do not change. The payment's refunded amount rises,
its unallocated credit falls, and the plan's `creditAmount` falls by the same amount.

### 6. Read the history

```bash
curl -s http://localhost:8080/api/claims/0000004711/payment-plan/payments
curl -s http://localhost:8080/api/claims/0000004711/payment-plan/refunds
```

Payments are ordered by `paidOn`, then id. Refunds are ordered by `refundedOn`, then id.

## API at a glance

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/claims` | Seed a claim. Demo and test use. |
| `GET` | `/api/claims/{claimNumber}` | Read one claim. |
| `GET` | `/api/claims/{claimNumber}/payment-plan` | Read the plan. |
| `POST` | `/api/claims/{claimNumber}/payment-plan` | Create the plan. |
| `POST` | `/api/claims/{claimNumber}/payment-plan/payments` | Record a payment. |
| `GET` | `/api/claims/{claimNumber}/payment-plan/payments` | Read payment history. |
| `POST` | `/api/claims/{claimNumber}/payment-plan/payments/{paymentId}/refunds` | Refund unallocated credit. |
| `GET` | `/api/claims/{claimNumber}/payment-plan/refunds` | Read refund history. |

The full request and response contract is in the [API reference](docs/api.md).

## Rules that affect every integration

### Claim numbers

- The response always uses a 10-digit zero-padded string: `4711` becomes `"0000004711"`.
- A request path accepts the padded or unpadded numeric form.
- The seed body takes a JSON number.
- A claim number must be positive and at most 10 digits. Anything else returns `400`.

### Money

- Money is a JSON number, normalized to two decimal places.
- A value that would need rounding to two decimal places returns `400`. Input is never silently
  rounded.
- There is one currency. The case study has no currency field.
- `amountPaid` means money allocated to installments. It does not include credit.

### Dates and time

- Business dates are `LocalDate`.
- The business time zone is `Europe/Berlin`.
- `paidOn` and `refundedOn` must not be in the future.
- `firstDueDate` must not be in the past.
- Due dates are monthly and calculated from the original first date.

### One plan per claim

A claim has zero or one plan. The plan is addressed through the claim, not through a plan id.
A second create returns `409`.

### Status

| Condition | Status |
| --- | --- |
| Outstanding balance is zero | `COMPLETED` |
| Any unpaid installment is past due | `OVERDUE` |
| Otherwise | `ACTIVE` |

`COMPLETED` wins. Due today is not overdue. Status is computed on every read, so it can change
without a write.

### Payments and credit

- Allocation order is due date, then installment number.
- Partial payments are allowed.
- One payment can span several installments.
- Overpayment is spread over future unpaid installments.
- Money left after all installments are paid becomes `creditAmount`.
- A payment on a completed plan is accepted as credit.

### Refunds

- A refund uses only the selected payment's unallocated credit.
- A refund does not change installment paid amounts or the outstanding balance.
- A refund does not reopen a completed plan.
- Partial and multiple refunds are allowed until the credit is exhausted.

### References, retries and concurrency

- A payment reference is unique per plan when present. A refund reference is unique per payment
  when present.
- A duplicate reference returns `409`.
- There is no idempotency key. A retry with the same reference returns `409`, not the original
  result. If a response is lost, read the current state before retrying.
- Payment and refund recording locks the claim row. Concurrent requests for one claim serialise.
- A lock conflict returns `409` with a retry message.

### Error body

```json
{
  "type": "about:blank",
  "title": "Payment plan not found",
  "status": 404,
  "detail": "No payment plan for claim 0000004711",
  "instance": "/api/claims/0000004711/payment-plan"
}
```

`400` means invalid input, `404` means an unknown claim, plan or payment, `409` means a
conflict, and `500` is a generic unexpected error.

## Configuration

Main settings in `src/main/resources/application.properties`:

| Setting | Value |
| --- | --- |
| Database | `jdbc:h2:mem:testdb` |
| Credentials | `sa` / `password` |
| Schema | generated by Hibernate |
| DDL failure | `hibernate.hbm2ddl.halt_on_error=true` |

The test profile uses a unique H2 database per context, `create-drop`,
`open-in-view=false` and the same DDL failure rule.

A real deployment needs a persistent database, migrations, authentication and a claim source.
Those are out of scope for the case study.

## Repository layout

```text
src/main/java/net/snobo
  config/       Clock bean
  db/entity/    Claim, PaymentPlan, Installment, Payment, PaymentAllocation, Refund
  db/repository Spring Data repositories
  domain/       Money, ClaimNumbers, InstallmentSchedule, Status
  rest/         Controller, error handler, request and response records
  service/      Transactions, orchestration and service exceptions
```

## Documentation

- [Documentation index](docs/README.md)
- [Architecture](docs/architecture.md) — context, domain model, data model, transactions and assumptions.
- [API reference](docs/api.md) — every endpoint, field and error case.
- [Slice 00 — Build, test and CI](docs/slices/00-build-test-and-ci.md)
- [Slice 01 — Foundation and read path](docs/slices/01-foundation-and-read-path.md)
- [Slice 02 — Create payment plan](docs/slices/02-create-payment-plan.md)
- [Slice 03 — Record payments](docs/slices/03-record-payments.md)
- [Slice 04 — Refunds and credit settlement](docs/slices/04-refunds-and-credit.md)

## Verification

The final local verification run:

| Metric | Result |
| --- | --- |
| Tests | 220 passed |
| Line coverage | 92.2% |
| Branch coverage | 82.4% |

Commands:

```bash
./mvnw test
./mvnw -B -ntp clean verify -Pcoverage
```

GitHub Actions has a build verification workflow and a SonarQube workflow. The Sonar workflow is
the one that runs the tests and publishes coverage. See
[Build, test and CI](docs/slices/00-build-test-and-ci.md) for the exact commands and the current
gaps.

## Production adoption checklist

Before using this service in a real system, add:

- authenticated and authorised access;
- a persistent database and schema migrations;
- a real claim source or import instead of `POST /api/claims`;
- an idempotency key and replay handling for payments and refunds;
- pagination for payment and refund history;
- a decision on multi-currency, interest, fees and accounting export;
- metrics, alerting and audit retention;
- explicit `spring.jpa.open-in-view=false` in the main profile;
- tuned lock timeouts and retry behaviour.

## Project background

- [Business requirements](business-requirements.md)
- [Technical requirements](technical-requirements.md)
- [Goals](goals.md)
