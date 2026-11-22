[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=DevSnobo_rate-payment-service-case-study&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=DevSnobo_rate-payment-service-case-study)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=DevSnobo_rate-payment-service-case-study&metric=coverage)](https://sonarcloud.io/summary/new_code?id=DevSnobo_rate-payment-service-case-study)

# rate-payment-service-case-study
This repo implements a case study of a Spring Boot rate payment service in Java with Maven as build tool.

## Overview
- technical requirements can be found in the [technical-requirements.md](technical-requirements.md) file.
- project goals can be found in the [goals.md](goals.md) file.
- business requirements can be found in the [business-requirements.md](business-requirements.md) file.

## Scenario
- a claim can be displayed and an installment plan can be created
- payments can be recorded and allocated to the open installments
- overdue rates, partial payments and overpayments can be handled

## Assumptions
- The claim is local to this service; there is no upstream claim system.
- One payment plan per claim.
- A claim number is a `Long`, positive and at most 10 digits. The API presents it as a
  10-digit zero-padded string (`4711` → `"0000004711"`); storage stays numeric.
- The claim owns the total amount. The plan's installments must sum to it.
- Single currency for the case study; there is no currency field.
- There is no customer/debtor aggregate and no claim-list endpoint. The service looks
  up one claim by number.
- Claims are seeded through a demo `POST /api/claims` endpoint; it is not full claim
  CRUD.
- Deleting claims or plans is out of scope.
- H2 in-memory is the demo database.

## API

### Claims

- `POST /api/claims` — demo seed endpoint.
- `GET /api/claims/{claimNumber}` — look up one claim.

### Payment plans

- `POST /api/claims/{claimNumber}/payment-plan` — create a plan.
- `GET /api/claims/{claimNumber}/payment-plan` — read the plan.

Create a plan either by number of installments:

```json
{
  "installmentCount": 9,
  "firstDueDate": "2026-12-01"
}
```

or by the amount the debtor can pay each month:

```json
{
  "installmentAmount": 80.00,
  "firstDueDate": "2026-12-01"
}
```

Rules:

- Provide exactly one of `installmentCount` or `installmentAmount`.
- `installmentCount` is between 1 and 120.
- `installmentAmount` must be positive, at most two decimals, and must not
  produce more than 120 installments.
- `firstDueDate` is optional. The default is the first day of the next month.
- Past due dates are rejected.
- Due dates are monthly, calculated from the first due date.
- The last installment receives the rounding remainder.
- If `installmentAmount` is greater than the claim total, the plan has one
  installment for the claim total.
- One payment plan per claim. A second create request returns 409.
- Creation returns `201 Created` with a `Location` header and the plan body.

### Payment allocation

Not implemented yet; planned for the payment slice:

- Allocate payments to the oldest due date first, then by installment number.
- Partial payments are allowed.
- An overpayment is spread over future unpaid installments when they exist.
- If no future installment remains, the excess is a credit/overpayment case handled
  by the refund rule.

## Potential features

- A quote endpoint, for example
  `POST /api/claims/{claimNumber}/payment-plan/quote`, could return the calculated
  schedule without persisting it. This would let a debtor compare a count-based and
  an amount-based offer before accepting one.