# Slice 01 — Foundation and read path

## Goal

Replace the first scaffold with a working claim model and a claim-keyed read path. The service
needs to own a claim, expose one claim by number and expose its payment plan with computed
totals and status.

## What landed

### Claim model

A local `Claim` entity is the aggregate root:

| Field | Type | Rule |
| --- | --- | --- |
| `claimNumber` | `Long` | Required, unique, positive, at most 10 digits. |
| `totalAmount` | `BigDecimal` | Required, positive, precision 19, scale 2. |
| `debtorName` | `String` | Required, not blank. |
| `createdOn` | `LocalDate` | Required. |
| `paymentPlan` | `PaymentPlan` | Optional one-to-one; the claim owns the unique foreign key. |

`Claim.createPaymentPlan()` links both sides and rejects a second plan. `PaymentPlan.claim` is
the inverse side.

### Plan and installments

`PaymentPlan` no longer stores a total, paid amount, outstanding balance or status. It derives
them from its installments. `Installment` stores the scheduled amount, the paid amount and the
due date. The collection is ordered by installment number.

`PaymentPlan.validateAgainstClaim()` checks three invariants:

- at least one installment;
- the sum of scheduled amounts equals the claim total;
- installment numbers are unique.

### Read endpoints

- `POST /api/claims` seeds a claim for demo and tests. It is not full claim CRUD.
- `GET /api/claims/{claimNumber}` reads one claim.
- `GET /api/claims/{claimNumber}/payment-plan` reads the plan.

The plan response is flat: padded `claimNumber`, total, paid, outstanding, status and ordered
installments. There is no nested claim object and no internal plan id.

### Computed status

Status is computed from the installments and the injected Berlin clock:

- `COMPLETED` when the outstanding balance is zero;
- `OVERDUE` when an unpaid installment is past due;
- `ACTIVE` otherwise.

`COMPLETED` wins. Due today is not overdue.

### Error contract

All errors use `ProblemDetail` from one advice:

- unknown claim or plan → `404`;
- duplicate claim → `409`;
- malformed input, body or claim number → `400`;
- unexpected error → `500` with a generic body and a server-side stack trace.

### Persistence safety

- Required columns are not null.
- Claim number is unique.
- Installments are unique per plan and number.
- Money columns are precision 19, scale 2.
- `hbm2ddl.halt_on_error=true` makes a broken schema fail startup.
- The test profile uses a unique H2 database per context, `create-drop` and
  `open-in-view=false`.

## Assumptions and rules

- The claim is local. There is no upstream claim system.
- One plan per claim. There is no plan history and no public plan id.
- The claim owns the total. The plan does not keep a second copy.
- Claim numbers are numeric in storage and zero-padded to 10 digits in the API.
- Required claim fields are claim number, total, debtor name and created date.
- There is no currency field. The case study is single-currency.
- There is no customer or debtor aggregate and no claim-list endpoint.
- Deletions of claims and plans are out of scope.
- Claims are seeded through `POST /api/claims` only.
- Status is computed on read. It is not stored or queryable in SQL, and it can change between
  two reads without a write.
- A plan with no installments is invalid.
- H2 is the database for the case study.

## Verification

The slice is covered by:

- entity tests for claim creation, claim-number bounds, linking and one-plan enforcement;
- money and claim-number tests;
- repository tests for lookup by claim number and entity-graph loading;
- service tests for read and seed validation;
- web tests for routes, padded claim numbers, JSON shape and `ProblemDetail` errors;
- full-context flows for seed, read and missing-plan handling.

The final project-wide suite contains 220 tests. The read-path slice is one part of it. See
[Build, test and CI](00-build-test-and-ci.md).

## Out of scope at this point

- Creating a plan. That is [slice 02](02-create-payment-plan.md).
- Recording payments and refunds. Those are slices 03 and 04.
- Claim updates, deletes and list endpoints.
- Multi-currency, interest and fees.

## Related code

- [`Claim`](../../src/main/java/net/snobo/db/entity/Claim.java)
- [`PaymentPlan`](../../src/main/java/net/snobo/db/entity/PaymentPlan.java)
- [`Installment`](../../src/main/java/net/snobo/db/entity/Installment.java)
- [`ClaimNumbers`](../../src/main/java/net/snobo/domain/ClaimNumbers.java)
- [`Money`](../../src/main/java/net/snobo/domain/Money.java)
- [`PaymentPlanService`](../../src/main/java/net/snobo/service/PaymentPlanService.java)
- [`ApiExceptionHandler`](../../src/main/java/net/snobo/rest/ApiExceptionHandler.java)
