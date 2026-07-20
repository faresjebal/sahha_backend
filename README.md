# sahha_backend
# Sahha — Telemedicine Platform for Underserved Regions in Tunisia

**Sahha** is a full-stack telemedicine platform designed to connect patients in underserved regions of Tunisia with qualified doctors through online appointment booking, secure authentication, real-time notifications, and digital prescription management.

The project is built as a serious learning and portfolio project to demonstrate backend engineering, frontend development, security, testing, DevOps, deployment, and system design skills.

---

## Problem

In many underserved regions, access to medical care can be limited by distance, availability of specialists, transportation difficulties, and administrative delays.

Patients may struggle to:

- Find doctors by specialty or region
- Book appointments efficiently
- Receive follow-up documents such as prescriptions
- Communicate with doctors in a structured digital system

Sahha aims to provide a simple, secure, and scalable telemedicine solution adapted to this context.

---

## Solution

Sahha allows:

- Patients to register, search doctors, book appointments, and download prescriptions
- Doctors to manage their profile, availability, appointments, and prescriptions
- Admins to approve doctors and supervise the platform
- Users to receive real-time notifications about appointment updates

---

## Main Features

### Authentication and Authorization

- User registration and login
- JWT-based authentication
- Role-based access control
- Roles:
  - Patient
  - Doctor
  - Admin

### Patient Features

- Complete patient profile
- Search doctors by specialty, region, and language
- Book appointments
- View upcoming and past appointments
- Receive real-time appointment notifications
- Download prescription PDFs

### Doctor Features

- Complete doctor profile
- Manage availability slots
- Confirm or cancel appointments
- Mark appointments as completed
- Create prescriptions for patients

### Admin Features

- Approve doctor accounts
- View platform activity
- Manage users and doctors

### Technical Features

- Secure REST API with Spring Boot
- MySQL database
- Angular frontend
- Real-time notifications using WebSocket/STOMP
- Redis caching for doctor search
- Kafka-based event flow for appointment notifications
- PDF generation for prescriptions
- Docker Compose for local development
- GitHub Actions for CI
- AWS deployment
- Monitoring with Spring Boot Actuator and Prometheus

---

## Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- MySQL
- JWT
- WebSocket / STOMP
- Redis
- Kafka
- JUnit 5
- Mockito

### Frontend

- Angular
- TypeScript
- Angular Router
- HTTP Interceptors
- Route Guards
- RxJS

### DevOps

- Docker
- Docker Compose
- GitHub Actions
- AWS EC2
- Prometheus
- Grafana

---

## Architecture Overview

```text
Patient / Doctor / Admin
        |
        v
Angular Frontend
        |
        v
Spring Boot REST API
        |
        |---- MySQL
        |---- Redis
        |---- Kafka
        |---- WebSocket Notifications
        |
        v
PDF Prescription Service

## UML Class Diagram

![UML Class Diagram](docs/ChatGPT%20Image%20Jul%2020,%202026,%2012_08_59%20PM.png)
