# ADR 0001: Initial Architecture

- Status: Accepted with Java baseline adjusted to the installed Java 23; framework compatibility pending verification.
- Date: 2026-11-20

## Decision

Use a modular Java 23/Spring Boot 4 backend with MyBatis-Plus and MySQL, Redis for ephemeral security state and acceleration, and a Vue 3/JavaScript SPA. Keep production deployment out of scope.

## Reason

The workstation has Java 23 installed and no Java 17 runtime. Using the available runtime unblocks local development while preserving the specified framework and library stack. Verify Spring Boot 4 and all selected dependencies on Java 23 before fixing versions.

## Follow-up

Verify compatible dependency versions against official documentation and a minimal local build before fixing versions.
