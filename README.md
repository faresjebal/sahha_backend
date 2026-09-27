# Sahha — healthcare platform (work in progress)

Sahha is a learning project exploring access to healthcare services in Tunisia. The intended workflows include finding a doctor, booking an appointment, and coordinating patient and clinician interactions.

## Repository status

This repository currently contains project documentation and an architecture image. It does **not** contain a runnable backend implementation. Features and infrastructure described below are plans, not completed or deployed capabilities. The separate [Sahha frontend](https://github.com/faresjebal/sahha_frontend) repository contains React and TypeScript source code.

## Intended backend scope

- Authentication and role-based access for patients, clinicians, and administrators
- Doctor discovery and appointment scheduling
- Clinical documents and notifications
- Service boundaries, persistence, and reliable event delivery

The backend implementation, tests, deployment, security review, and integration with the frontend remain in progress. Specific technologies will be documented here when their code is committed and reproducible.

## Why this exists

I am using Sahha to practice designing a healthcare workflow and implementing it incrementally. The repository status above distinguishes the current code from the target architecture so contributors and reviewers can evaluate it accurately.
