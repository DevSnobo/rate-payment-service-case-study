# AI prompts — rate payment service case study

Every user prompt in this repository is recorded here, verbatim, in the order it was given.
Each entry has a number, the prompt, and a short record of decisions and outcome. No dates.

---

## 001 — 2026-11-21 — Project rules and AGENTS.md

```text
this is a case study for a job interview: 
do not commit changes, unless i explicitly ask you to
do not automatically implement anything. I will ask you for specific things. if there are gaps that I have not thought of, bring them up to me for discussion and detailed review. do not make assumptions of what I want - ask me and we will find out how to move forward
we will work together whenever I ask you things, do not work standalone for me
document all of my prompts in a file called "ai-prompts.md" in a format that can be nicely read


write these rules into an AGENTS.md file that will force you to adhere to them at all times.
```

**Outcome**

Created `AGENTS.md` with the project rules and this prompt log. No other files were changed.

---

## 002 — Prompt log format

```text
1. prompt text + decisions reached/short outcome. keep it very brief to give a bit of context to an interviewer checking the prompts, but don't overwhelm them with prose
2. no dates necessary at all as this is a weekend project. leave the first entry as is
```

**Decisions**

- Each entry holds the prompt verbatim plus a brief decisions/outcome note.
- No dates in new entries; entry 001 is left unchanged.

## 003 — Critique of the first service-to-db slice

```text
I implemented the first service to db slice. give me an honest critique. test setup is currently missing, as i'm not yet familiar enough with Spring's ways for testing the different layers best. give me suggestions for improvements
```

**Decisions**

- Reviewed the slice against the requirements; ran the build/tests and the app, and verified two blocking defects in a throwaway copy under `/tmp`.
- Reported findings and a layered test strategy. No source files were changed; only this prompt was appended.

## 004 — Model, API, test and documentation follow-up

```text
questions to 2:

suggestions for no drift between the 3 fields?
timezone is UTC+1 (Berlin), good suggestion for the Clock in tests.
are the constrains done right in the entities?
the status OVERDUE will be computed on read from installments + Clock - this is an assumption we make here
good point, package-private is the first step. recordPayment and recalculateStatus(clock) are good additions. 
outline what you would see in the claim model. honestly missed this one in the first pass.

questions to 3:
good improvements on the API, will adjust that. outline the suggested layout with the nested claim form

questions to 4:
the testdb isolation, give details
docs/decisions.md sounds good, let's do that

questions to 5:
I like the approach, add one example test each, blocking out the idea of the tested layer. then I can expand on this approach. but also note how the tests would change when we adjust the models and rest controller paths

create a document with the main tests for the business cases and some of their failure cases so i have a checklist for basic cases.

direct answers to your decisions:
1. already changed.
2. a plan addressed by claim id would make most sense to me
3. already changed to BigDecimal. check if the rounding is done right
4. yes, oldest first. overpayment is kept as credit/allocated/spread to future installments. overpayments when the claim is completed should be managed otherwise, most likely a refund on the extra amount
5. first approach is computed on read.
6. Berlin
7. finish read first with the cleanup of the rest paths. then we land this PR and continue with the create-plan step
8. we stay on h2 for this project


I already addressed some of your points. for those i did not answer to, assume I want more details.
i want you to write these improvement points to todo.md in the repository with more detail.

as always here, do not implement standalone.
```

**Decisions**

- Recorded the accepted rules (claim-keyed read path, BigDecimal, oldest-first
  allocation, computed status, Berlin clock, H2) and the open questions.
- Created `todo.md`, `docs/decisions.md` and `docs/test-checklist.md`. No main or test
  source files were changed; the example tests are sketches in the backlog until the
  model refactor lands.

---

## 005 — Claim model and relation review

```text
walk me through the different open todos one by one in order to make this less overwhelming.

let's start with the claim model and its relation to the other entities. what is still open to do or fix?
```

**Decisions**

- Review only; no source changes. Reported the compile break, the two-sided `mappedBy`,
  the duplicated claim total, the stale plan unique constraint, the path mismatch and
  the open decisions (local/external claim, cardinality, claim-number type, customer
  concept, claim lifecycle).

---

## 006 — Claim model, service structure and error handling

```text
I fixed the unique constraints.

Claim owns the PaymentPlan. implement it in that way
stale constraint is removed. add the correct definition, if it is missing somewhere
setter is fixed. factories right now in the entities, if you deem it better.

we will write DataJpaTests for all entities when the model is fixed. then we will also incrementally add/cover the error cases


decisions answers:
claim is local.
one plan per claim, with the ledger for the progress on installments added later. this is important for auditing the history of the debitor
claimNumber is a long and you should adjust the controller to also use it. no customers/debitors that may check their list of claims, only a lookup of one single claim. and in the future slices the creation of a payment plan for this claim + view of the installments
it would be nicer to use a 0-padded number for the claimNumber. adjust everything accordingly
the claim owns its total.
yes, document and validate.
already mentioned, no customers/debitors
a small POST endpoint for me to seed the claims for the test/demo
yes, 404 handling for create-plan slice
I decide right now that deletions of claims or plans is not in scope for this demo.
use GET /api/claims/{claimNumber}/payment-plan with flat claimNumber and the plan fields
currency is not needed, debtorName should be kept and required. test data can use example names. createdOn also required.

add the constraints and invariants.

fix up the model and cover it in tests. 

commit the code and tests related to these previous tasks to feature/fix-db-model in incremental commits.

afterwards, create a stacked branch on top of feature/fix-db-model named "feature/fix-service-structure" and continue with the incremental commits
then go ahead and fix the controller/services and handle the error cases in the services and cover them with respective tests as we've already outlined.

use subagents where you can parallelize your work. work in the same main working tree, don't start cloning into separate worktrees. coordinate the agents to not sabotage their test efforts
```

**Decisions**

- Local `Claim` owns one `PaymentPlan`; claim number is a `Long` presented as a
  10-digit zero-padded string; the claim owns the total; `debtorName` and `createdOn`
  are required; no customer list, no currency, no deletions.
- Added the demo `POST /api/claims` seed endpoint and `GET
  /api/claims/{claimNumber}/payment-plan` with a flat response.
- Model and `@DataJpaTest` coverage on `feature/fix-db-model`; service/controller,
  `ProblemDetail` error handling and service/web/full-context tests on the stacked
  `feature/fix-service-structure` branch. 54 tests pass.

---

## 007 — Create-plan slice rundown

```text
let's continue to the create-plan slice. give me the rundown of what needs to be implemented in easy to digest steps. once I give the signal, you may implement this and commit incrementally
```

**Decisions**

- Planning only; no code changes and no commits. Outlined the create-plan contract,
  validation, installment split, errors, tests, branch strategy and the decisions that
  still need the user's answer.

---

## 008 — Create-plan implementation

```text
yes, installmentAmount

the two scenarios look good how you outlined them.
regarding the edge case of overpaid installments. these should be allocated/spread on the future installments, if there are any further ones.
otherwise good assumptions. document those

rules:
max of 120 is generous, but would suffice in this case study. first of month is also a good default here. no past dates allowed.

the quote would be a good convenience feature, we can add it if there's more time. add it in the readme as potential feature.

remainder on last installment, yes

a note on the gitignored markdowns. those are for your progress tracking, not pushed to the repo. use them, but don't mention them again

and now, incremental commits, with tests and error handling, go implement. give me some occasional report of where you are in your tasks
```

**Decisions**

- Implemented the create-plan slice with `installmentCount` or `installmentAmount`,
  max 120 installments, first-of-month default due date, no past due dates, and the
  remainder on the last installment.
- Documented the payment allocation rule: oldest due date first, then installment
  number; overpayments spread to future installments when present; the remaining excess
  is a refund/credit case.
- Added the quote endpoint to the README as a potential feature.
- New stacked branch `feature/create-plan`; 122 tests pass.
