# Architecture

## Baseline

The intended architecture is a modular Spring Boot 4 application with the available Java 23 runtime and Maven. Keep HTTP controllers, application services, domain rules, persistence mappers, security, and infrastructure adapters as explicit boundaries. Use MyBatis-Plus with MySQL; Redis supports short-lived challenges and throttling, while durable revocation facts remain in MySQL. A Vue 3/Vite single-page app communicates through the versioned REST API. No runtime modules have been created yet.

## Initial Delivery Order

1. Identity and teaching foundation.
2. Grade writes and transactionally consistent derived data.
3. Imports/exports, profile and avatar storage.
4. Complete role workflows and acceptance review.

## Compatibility Decisions

See [ADR 0001](adr/0001-runtime-compatibility.md) for the verified Java 23/Spring Boot 4 build and local startup baseline. The current application uses JDBC for the initial vertical slice while MyBatis-Plus is present and loaded; replace ad hoc JDBC calls with mapper boundaries as domains mature. AOP grade derivation runs in `BEFORE_COMMIT` on the same transaction and is source-tested only by the HTTP smoke path so far.

## Boundaries

Authorization belongs in backend services and scoped queries, not only the UI. MySQL constraints are authoritative for uniqueness and relationships. COS is behind a storage adapter and must fail clearly when unconfigured. No production deployment design is in scope.
