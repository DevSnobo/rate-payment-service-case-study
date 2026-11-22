# Slice 02 — Create payment plan

## Goal

Let a client create the one payment plan for an existing claim. The client chooses either a
number of installments or a monthly amount. The server calculates the schedule from the
claim-owned total.

## Endpoint

```text
POST /api/claims/{claimNumber}/payment-plan
```

The response is `201 Created` with `Location: /api/claims/{claimNumber}/payment-plan` and the
same flat plan body as the read endpoint.

## Request contract

The request provides exactly one mode.

### Count mode

```json
{
  "installmentCount": 9,
  "firstDueDate": "2026-12-01"
}
```

### Amount mode

```json
{
  "installmentAmount": 80.00,
  "firstDueDate": "2026-12-01"
}
```

`firstDueDate` is optional. The default is the first day of the next month, calculated from the
Berlin clock.

Both fields present or neither field present is `400`.

## Schedule rules

### Count mode

For a total `T` and count `N`:

```text
regularAmount = T / N, rounded DOWN to 2 decimals
installments 1..N-1 = regularAmount
installment N       = T - regularAmount * (N - 1)
```

Example: `500.00` over 9:

```text
8 × 55.55 + 1 × 55.60
```

The last installment carries the remainder.

### Amount mode

For a total `T` and monthly amount `A`:

```text
fullCount = floor(T / A)
full installments = fullCount × A
remainder = T - fullCount × A
```

- If `remainder` is positive, it becomes one finishing installment.
- If `A` divides `T` exactly, there is no finishing installment.
- If `A` is greater than `T`, the plan has one installment for `T`.
- If `fullCount` exceeds 120, the request is rejected. If it is exactly 120 and a remainder
  would add a 121st installment, the request is also rejected.

Examples for `500.00`:

```text
A = 80.00  -> 6 × 80.00 + 1 × 20.00
A = 100.00 -> 5 × 100.00
A = 600.00 -> 1 × 500.00
```

## Dates

- Due dates are monthly.
- The first due date is `firstDueDate` if supplied, otherwise the first day of the next month.
- Each due date is calculated from the original first date with `plusMonths(n - 1)`. This avoids
  month-end drift: January 31 → February 28 → March 31.
- A past `firstDueDate` is rejected. Due today is allowed.
- A plan created with a future or current first date starts `ACTIVE`. Past first dates are
  rejected, and due today is not overdue.

## Constants

| Constant | Value | Where |
| --- | --- | --- |
| Maximum installments | `120` | `InstallmentSchedule.MAX_INSTALLMENTS` |
| Money scale | `2` | `Money.SCALE` |
| Claim number width | `10` | `ClaimNumbers.WIDTH` |

## Errors

| Status | Case |
| --- | --- |
| `201` | Plan created. |
| `400` | Both or neither mode, invalid count, invalid amount, amount needing rounding, a schedule that would create a zero amount or more than 120 installments, a past first due date, malformed body or claim number. |
| `404` | Unknown claim. |
| `409` | The claim already has a plan. |

The service also catches a unique-constraint race on the one-plan relation and maps it to `409`.

## Assumptions and product decisions

- The server calculates the schedule. The client does not submit a list of installments.
- The two client-facing choices are "how many installments" and "how much can I pay each month".
- Monthly cadence only.
- The remainder goes on the last installment.
- The maximum is 120 monthly installments, about ten years.
- The plan is created immediately. There is no quote or preview endpoint. Comparing two offers
  before accepting one is a potential follow-up feature.
- A second create is not idempotent. It returns `409` because a plan already exists.
- The client does not choose the installment numbers. The server assigns `1..N`.
- No interest, fees, down payment or balloon payment.

## Verification

Coverage includes:

- exact and non-divisible splits in both modes;
- remainder placement;
- amount greater than the total;
- exact division with no finishing installment;
- tiny finishing installment;
- maximum count and too-low amount;
- month-end due dates;
- default first-of-month date;
- past first due date;
- both and neither modes;
- unknown claim, existing plan and unique-constraint race;
- web contract, `Location` header and `ProblemDetail` errors;
- a full create-then-read flow.

See [Build, test and CI](00-build-test-and-ci.md) for the project-wide test and coverage
numbers.

## Out of scope

- Quote and preview.
- Plan history or a second plan for a claim.
- Plan deletion.
- Explicit client-supplied installment rows.
- Interest and fees.

## Related code

- [`InstallmentSchedule`](../../src/main/java/net/snobo/domain/InstallmentSchedule.java)
- [`PaymentPlanService`](../../src/main/java/net/snobo/service/PaymentPlanService.java)
- [`CreatePaymentPlanRequest`](../../src/main/java/net/snobo/rest/dto/CreatePaymentPlanRequest.java)
