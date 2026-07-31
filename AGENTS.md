# Sahha repository guidance

## Active objective

Build the Sahha internship Version 1 described in:

- `docs/PROJECT_CONTEXT.md`
- `docs/INTERNSHIP_PLAN.md`

The internship workflow is the active scope. Future healthcare modules must not
be implemented unless the user explicitly expands the scope.

## Frontend baseline

The approved React frontend was migrated from
`C:\Users\LENOVO\Desktop\codex` into this repository's `frontend` directory on
2026-07-25. The in-repository copy is now the implementation source of truth.

- Treat the original external frontend as read-only reference material.
- Do not regenerate or replace the migrated design.
- Reuse its visual language, responsive behavior, role-aware routes, mock
  workflows, typed contracts, and reusable components.
- Do not treat its out-of-scope Aegis screens as Sahha internship requirements.

## Required working method

Before implementation:

1. Read the project context and current plan.
2. Confirm the current milestone and its exit criteria.
3. Inspect existing code before changing it.

After every implementation, configuration, schema, test, or documentation
change:

1. Update `docs/INTERNSHIP_PLAN.md` in the same task.
2. Mark only verified work as complete.
3. Record validation evidence and important decisions.
4. Set one clear next task.
5. Add a dated change-log entry.

## Non-negotiable constraints

- Enforce organisation context and resource-level authorisation on the backend.
- Administrative roles do not receive clinical access by implication.
- Messaging does not grant patient-record access.
- Sharing is explicit, minimal, revocable, expirable, consent-aware, and audited.
- Finalised clinical information is immutable; corrections are append-only and
  attributable.
- Each microservice owns its data. No service writes another service's database.
- Medical files are private and use short-lived authorised access.
- Use only synthetic or properly anonymised internship data.
- The browser talks to the API gateway, never directly to internal services or
  Kafka.
- Preserve unrelated user changes.

## Completion standard

A task is complete only when its relevant build, automated tests, security
checks, and integration checks pass. A screen backed only by mock data is not a
completed backend-integrated feature.
