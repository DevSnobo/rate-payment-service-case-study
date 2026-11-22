# API reference

Base path: `/api`. All requests and responses are JSON. Errors are `ProblemDetail` (RFC 9457).

## Conventions

| Item | Rule |
| --- | --- |
| Claim number in a response | A 10-digit zero-padded string, for example `"0000004711"`. |
| Claim number in a request path | A numeric value. Padded and unpadded forms both work: `/api/claims/4711/...` and `/api/claims/0000004711/...`. |
| Claim number in the seed body | A JSON number, for example `4711`. |
| Money | A JSON number, normalized to two decimal places. A value that would need rounding is rejected with `400`. |
| Business date | `yyyy-MM-dd`, for example `"2026-12-01"`. |
| Timestamp | ISO-8601 instant, for example `"2026-11-22T06:58:53Z"`. |
| Status | `ACTIVE`, `OVERDUE` or `COMPLETED`, computed on read. |
| Reference | Optional. Trimmed. Blank becomes `null`. Maximum 255 characters. |
| Concurrency conflict | `409` with a retry message. |

## Endpoint summary

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/claims` | Seed a claim. Demo and test use. |
| `GET` | `/api/claims/{claimNumber}` | Read one claim. |
| `GET` | `/api/claims/{claimNumber}/payment-plan` | Read the claim's plan. |
| `POST` | `/api/claims/{claimNumber}/payment-plan` | Create the claim's plan. |
| `POST` | `/api/claims/{claimNumber}/payment-plan/payments` | Record a payment. |
| `GET` | `/api/claims/{claimNumber}/payment-plan/payments` | Read payment history. |
| `POST` | `/api/claims/{claimNumber}/payment-plan/payments/{paymentId}/refunds` | Refund part of a payment's unallocated credit. |
| `GET` | `/api/claims/{claimNumber}/payment-plan/refunds` | Read refund history for the plan. |

There are no delete endpoints.

## Seed a claim

```text
POST /api/claims
```

Request:

```json
{
  "claimNumber": 4711,
  "totalAmount": 500.00,
  "debtorName": "Ada Lovelace",
  "createdOn": "2026-11-21"
}
```

Rules:

- `claimNumber` is required, positive and at most 10 digits.
- `totalAmount` is required, positive and scale 2.
- `debtorName` is required and must not be blank.
- `createdOn` is required. There is no past-date restriction.
- A duplicate claim number returns `409`.
- A malformed body or an invalid field returns `400`.
- The response is `201 Created` with `Location: /api/claims/{claimNumber}`.

Response:

```json
{
  "claimNumber": "0000004711",
  "totalAmount": 500.00,
  "debtorName": "Ada Lovelace",
  "createdOn": "2026-11-21"
}
```

## Read a claim

```text
GET /api/claims/{claimNumber}
```

Response `200 OK`:

```json
{
  "claimNumber": "0000004711",
  "totalAmount": 500.00,
  "debtorName": "Ada Lovelace",
  "createdOn": "2026-11-21"
}
```

Errors: unknown claim `404`; non-numeric, zero or more than 10 digits `400`.

## Read a payment plan

```text
GET /api/claims/{claimNumber}/payment-plan
```

Response `200 OK`:

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
    {
      "installmentNumber": 1,
      "scheduledAmount": 166.66,
      "amountPaid": 0.00,
      "dueDate": "2026-12-01"
    },
    {
      "installmentNumber": 2,
      "scheduledAmount": 166.66,
      "amountPaid": 0.00,
      "dueDate": "2027-01-01"
    },
    {
      "installmentNumber": 3,
      "scheduledAmount": 166.68,
      "amountPaid": 0.00,
      "dueDate": "2027-02-01"
    }
  ]
}
```

Errors:

- Unknown claim: `404`, title `Claim not found`.
- Claim exists but has no plan: `404`, title `Payment plan not found`.
- Invalid claim number: `400`.

## Create a payment plan

```text
POST /api/claims/{claimNumber}/payment-plan
```

The request provides exactly one of `installmentCount` or `installmentAmount`.
`firstDueDate` is optional. It defaults to the first day of the next month.

### Count mode

```json
{
  "installmentCount": 3,
  "firstDueDate": "2026-12-01"
}
```

For a `500.00` claim:

- regular amount = `500.00 / 3` rounded down to two decimals = `166.66`;
- installments 1 and 2 are `166.66`;
- the last installment receives the remainder `166.68`.

### Amount mode

```json
{
  "installmentAmount": 80.00,
  "firstDueDate": "2026-12-01"
}
```

For a `500.00` claim:

- six full installments of `80.00`;
- one finishing installment of `20.00`;
- seven monthly installments in total.

If the amount divides the total exactly, there is no finishing installment. If the amount is
greater than the total, the plan has one installment for the total.

### Rules

- Exactly one of `installmentCount` or `installmentAmount` is required. Both or neither is `400`.
- `installmentCount` is between 1 and 120.
- `installmentAmount` is positive and scale 2. It must not produce more than 120 installments.
  A full count above 120 is rejected. A full count of 120 plus a finishing installment is also
  rejected.
- A count that would make the regular amount zero is rejected.
- `firstDueDate` must not be in the past. Due today is allowed.
- Due dates are monthly, calculated from the original first due date. For example,
  January 31 → February 28 → March 31.
- The remainder goes on the last installment.
- One plan per claim. A second create request returns `409`.
- The plan is created immediately. There is no quote or preview endpoint.
- The response is `201 Created` with `Location: /api/claims/{claimNumber}/payment-plan`.
- The body is the same plan response as the read endpoint.

Errors: `400` for an invalid request, `404` for an unknown claim, `409` for an existing plan.

A retry of a successful create is not idempotent. It returns `409` because the plan already
exists. Read the plan before retrying.

## Record a payment

```text
POST /api/claims/{claimNumber}/payment-plan/payments
```

Request:

```json
{
  "amount": 200.00,
  "paidOn": "2026-11-21",
  "reference": "bank-transfer-123"
}
```

Rules:

- `amount` is required, positive and scale 2.
- `paidOn` is required and must not be in the future relative to the Berlin clock.
- `reference` is optional and unique per plan when present. A duplicate returns `409`.
- The server records `recordedAt` and generates the public `paymentId` as a UUID.
- Allocation order is due date, then installment number.
- Each installment receives at most its outstanding amount.
- A payment may span several installments.
- An overpayment is spread over future unpaid installments while any remain.
- If money remains after every installment is paid, it is unallocated credit on the payment.
- A payment on a completed plan is accepted and recorded as credit.
- Recording returns `201 Created` with the payment and the updated plan.

Response (abridged installments and plan fields):

```json
{
  "payment": {
    "paymentId": "6c8c6d30-6d9b-4e5e-9f2b-3f4c5d6e7f80",
    "amount": 200.00,
    "paidOn": "2026-11-21",
    "recordedAt": "2026-11-21T10:15:30Z",
    "reference": "bank-transfer-123",
    "allocatedAmount": 200.00,
    "refundedAmount": 0.00,
    "unallocatedAmount": 0.00,
    "allocations": [
      {"installmentNumber": 1, "amount": 166.66, "dueDate": "2026-12-01"},
      {"installmentNumber": 2, "amount": 33.34, "dueDate": "2027-01-01"}
    ],
    "refunds": []
  },
  "paymentPlan": {
    "claimNumber": "0000004711",
    "totalClaimAmount": 500.00,
    "amountPaid": 200.00,
    "outstandingBalance": 300.00,
    "creditAmount": 0.00,
    "refundedAmount": 0.00,
    "status": "ACTIVE",
    "installments": ["..."]
  }
}
```

Errors:

- unknown claim or plan: `404`;
- invalid amount, date or body: `400`;
- duplicate reference: `409`;
- lock or data conflict: `409`.

## Read payment history

```text
GET /api/claims/{claimNumber}/payment-plan/payments
```

Returns an array of payment responses ordered by `paidOn`, then internal id. Each payment
contains its allocations and nested refunds. Errors are the same as the plan read.

## Record a refund

```text
POST /api/claims/{claimNumber}/payment-plan/payments/{paymentId}/refunds
```

A refund is taken only from the selected payment's unallocated credit. It never reduces money
already allocated to an installment.

Request:

```json
{
  "amount": 20.00,
  "refundedOn": "2026-11-21",
  "reference": "refund-123"
}
```

Rules:

- `amount` is required, positive, scale 2 and at most the payment's remaining unallocated credit.
- `refundedOn` is required and must not be in the future.
- Partial refunds and multiple refunds per payment are allowed.
- `reference` is optional and unique per payment when present. A duplicate returns `409`.
- A refund does not change installment paid amounts or the outstanding balance.
- A refund does not reopen a completed plan.
- The server records `recordedAt` and generates the public `refundId` as a UUID.
- Recording returns `201 Created` with the refund, the updated payment and the updated plan.

The example below assumes a `550.00` payment on a `500.00` plan. The payment leaves `50.00`
credit, and a `20.00` refund leaves `30.00`.

Response:

```json
{
  "refund": {
    "refundId": "d4f1b2c3-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "paymentId": "6c8c6d30-6d9b-4e5e-9f2b-3f4c5d6e7f80",
    "amount": 20.00,
    "refundedOn": "2026-11-21",
    "recordedAt": "2026-11-21T10:20:00Z",
    "reference": "refund-123"
  },
  "payment": {
    "paymentId": "6c8c6d30-6d9b-4e5e-9f2b-3f4c5d6e7f80",
    "amount": 550.00,
    "allocatedAmount": 500.00,
    "refundedAmount": 20.00,
    "unallocatedAmount": 30.00,
    "allocations": ["..."],
    "refunds": ["..."]
  },
  "paymentPlan": {
    "claimNumber": "0000004711",
    "totalClaimAmount": 500.00,
    "amountPaid": 500.00,
    "outstandingBalance": 0.00,
    "creditAmount": 30.00,
    "refundedAmount": 20.00,
    "status": "COMPLETED",
    "installments": ["..."]
  }
}
```

Errors: unknown claim, plan or payment `404`; invalid amount or date `400`; amount above the
remaining credit `409`; duplicate reference `409`; lock conflict `409`.

## Read refund history

```text
GET /api/claims/{claimNumber}/payment-plan/refunds
```

Returns an array of refund responses for the plan, ordered by `refundedOn`, then internal id.
Errors are the same as the plan read.

## Status rules

| Condition | Status |
| --- | --- |
| Outstanding balance is zero | `COMPLETED` |
| Any unpaid installment is past due | `OVERDUE` |
| Otherwise | `ACTIVE` |

- Due today is not overdue.
- `COMPLETED` wins over `OVERDUE`.
- Status is computed on every read. It can change without a write.
- A refund does not reopen a completed plan.

## Derived values

```text
amountPaid          = sum(installment.amountPaid)
outstandingBalance  = sum(installment.scheduledAmount - installment.amountPaid)
creditAmount        = sum(payment.unallocatedAmount)
refundedAmount      = sum(refund.amount)

allocatedAmount     = sum(allocation.amount)
unallocatedAmount   = payment.amount - allocatedAmount - refundedAmount

grossReceived       = amountPaid + creditAmount + refundedAmount
currentlyHeld       = amountPaid + creditAmount
```

`grossReceived` and `currentlyHeld` are explanatory identities, not fields in the response.

## Error response

```json
{
  "type": "about:blank",
  "title": "Payment plan not found",
  "status": 404,
  "detail": "No payment plan for claim 0000004711",
  "instance": "/api/claims/0000004711/payment-plan"
}
```

Status mapping:

| Status | Cases |
| --- | --- |
| `400` | Malformed JSON, invalid claim number, invalid field, amount that would need rounding, missing date, past or future date where not allowed, both or neither plan mode. |
| `404` | Unknown claim, plan or payment. |
| `405` | Method not supported for the path. |
| `409` | Duplicate claim, existing plan, duplicate payment or refund reference, over-refund, insufficient credit, lock or data conflict. |
| `415` | Unsupported content type. |
| `500` | Unexpected error. The body is generic. |

## Idempotency and retries

There is no idempotency key. A `reference` is a duplicate guard, not a replay mechanism.

- A repeated payment or refund with the same reference returns `409`.
- A repeated plan create returns `409`.
- If a client loses a successful response, it must read the current state before retrying.
- Payment and refund recording uses a pessimistic lock per claim. Concurrent requests for the
  same claim serialise. A lock failure returns `409` with a retry message.
