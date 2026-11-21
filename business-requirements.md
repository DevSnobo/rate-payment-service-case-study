# Business Requirements

This document outlines the business requirements for the project.

## Creating a payment plan
For an existing claim, a payment plan can be created.

## Show a payment plan
An existing payment plan can be requested. 
- claim number
- total claim amount
- outstanding balance
- amount paid
- status
- all installments with their due dates

## Record payments
Payments can be reported for a claim. The service must allocate the payment. 

## Calculate outstanding balance
When payments arrive they must be allocated across outstanding installments. Define a reasonable allocation rule and document it.

## Status of payment plan 
A payment plan can have the following statuses:
- ACTIVE
- COMPLETED
- OVERDUE

Define and implement verifiable status transitions.
