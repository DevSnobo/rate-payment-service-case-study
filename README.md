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