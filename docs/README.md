# Documentation

This service is a small, self-contained rate payment service. The documentation is split by
audience and by implementation slice, so a reader can start with the contract and then follow
how each rule reached the code.

## Start here

- [Architecture](architecture.md) — system context, domain model, money, time, status, transactions and persistence.
- [API reference](api.md) — endpoints, request and response bodies, field meanings, error contract and examples.
- [Build, test and CI](slices/00-build-test-and-ci.md) — how the project is verified.

## Implementation slices

The service was built in five slices. Each page records the goal, the rules that landed, the
edge cases and the verification for that slice.

0. [Build, test and CI](slices/00-build-test-and-ci.md) — toolchain, test layers, coverage and workflows.
1. [Foundation and read path](slices/01-foundation-and-read-path.md) — claim model, claim-keyed read API, computed status and error contract.
2. [Create payment plan](slices/02-create-payment-plan.md) — count and amount modes, schedule calculation and constraints.
3. [Record payments](slices/03-record-payments.md) — payment ledger, allocation, overpayment credit and concurrency.
4. [Refunds and credit settlement](slices/04-refunds-and-credit.md) — refunds against unallocated credit and the resulting arithmetic.

## Project background

- [Business requirements](../business-requirements.md)
- [Technical requirements](../technical-requirements.md)
- [Goals](../goals.md)
- [How a real integration uses the service](../README.md)
