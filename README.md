# Sahha — healthcare collaboration platform

Sahha is my software engineering internship project: a healthcare platform for organisations, clinicians, reception staff, and patients. It follows a patient journey from registration and appointment booking through consultation, private files, messaging, and consent-aware referrals.

**The current application source is on [`feature/organisation-service`](https://github.com/faresjebal/sahha_backend/tree/feature/organisation-service).** This `main` branch contains an early concept README and diagram and does not contain the application. Read the [source branch README](https://github.com/faresjebal/sahha_backend/blob/feature/organisation-service/README.md) for setup commands and the [living implementation plan](https://github.com/faresjebal/sahha_backend/blob/feature/organisation-service/docs/INTERNSHIP_PLAN.md) for verified progress and remaining work.

## Current implementation

- Java 21 and Spring Boot services for identity, organisations, patients, scheduling, clinical records, communication, notifications, protected files, and audit foundations.
- React and TypeScript frontend in the source branch's `frontend/` directory.
- Service-owned PostgreSQL databases with Flyway migrations; Gateway, Eureka, Kafka, and selected Redis use.
- Automated backend, frontend, and native synthetic workflow checks documented in the source branch.

The latest recorded full live acceptance run did **not** pass all stages. Shared-care/browser and two-doctor treatment verification, clean-machine acceptance, and Azure deployment remain in progress. This is an active project, not a deployed clinical product. Only synthetic or properly anonymised data is intended for development.

## Where to look

| Area | Path on the source branch |
| --- | --- |
| Backend modules | [Source branch root](https://github.com/faresjebal/sahha_backend/tree/feature/organisation-service) |
| React frontend | [`frontend/`](https://github.com/faresjebal/sahha_backend/tree/feature/organisation-service/frontend) |
| Project scope | [`docs/PROJECT_CONTEXT.md`](https://github.com/faresjebal/sahha_backend/blob/feature/organisation-service/docs/PROJECT_CONTEXT.md) |
| Progress and validation | [`docs/INTERNSHIP_PLAN.md`](https://github.com/faresjebal/sahha_backend/blob/feature/organisation-service/docs/INTERNSHIP_PLAN.md) |

The old concept image is retained in the history of this branch. It does not describe the current architecture.
