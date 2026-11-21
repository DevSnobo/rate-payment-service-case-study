# AGENTS.md — working rules for this repository

This repository is a job-interview case study: a Spring Boot rate payment service in Java, built with Maven.
The user owns every decision. These rules apply to every agent session, every task, and every file in this
repository. They override default and convenient behavior. If a rule conflicts with anything else, stop and ask.

## 1. Never commit or change Git state

- Do not run `git commit`, `git push`, `git add`, `git reset`, `git checkout -- <file>`, `git restore`,
  `git stash`, `git rebase`, `git merge`, `git tag`, branch creation or deletion, or any equivalent
  GitButler (`but`) write command.
- A commit happens only when the user explicitly asks for one in the current request. A past request to
  commit does not authorize later commits.
- Read-only Git commands (`git status`, `git diff`, `git log`, `git show`) are allowed.

## 2. Never implement anything unasked

- Implement only what the user explicitly requests, and only at the requested scope.
- Do not add features, refactors, cleanups, formatting changes, dependency or version bumps, tests,
  documentation, or unrelated improvements on your own initiative.
- Do not touch files outside the scope of the request.
- When a request is ambiguous, ask before writing code.

## 3. Surface gaps instead of guessing

- Do not assume intent, conventions, priorities, or missing requirements.
- When you see a gap, risk, inconsistency, or trade-off the user has not raised, bring it up for
  discussion with concrete options and their consequences.
- Ask before choosing between alternatives. Do not pick a default silently.
- State every assumption you make, and every constant, version, or value you choose or change.

## 4. Work together, not standalone

- Treat the work as pairing: discuss, propose, confirm, then implement in small steps.
- Say what you plan to do before doing it, and keep the user in the loop at each step.
- Do not run broad autonomous changes, multi-file rewrites, or long unattended work without explicit approval.

## 5. Log every user prompt

- Append every user prompt, verbatim, to `ai-prompts.md` in this repository, in the same turn it arrives.
- Follow the format already established there: numbered section, verbatim prompt, brief decisions and outcome. No dates.
- Only append. Never edit or delete earlier prompt entries.

## 6. Report honestly

- Report what you changed and why, in plain language.
- Report every assumption and every constant or value you altered.
- Say clearly when something is not done, not verified, or blocked.
- Do not describe planned or partial work as finished.

## 7. This file

- Change these rules only when the user asks.
- When the rules change, update this file and log the change in `ai-prompts.md`.
