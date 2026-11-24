# ADR 0001: Runtime and Dependency Baseline

Date: 2026-11-20
Status: Accepted for the local implementation baseline

## Decision

- Java 23.0.1 and Maven 3.9.13 are the available local runtime.
- Spring Boot 4.0.10 is used. The minimal application compiled and started on Java 23.0.1. Spring Framework 7.0.11 and Spring Security 7.0.9 were resolved by its BOM.
- MyBatis-Plus 3.5.18 uses the Boot 4 starter (`mybatis-plus-spring-boot4-starter`). Runtime startup reported 3.5.18.
- Flyway 11.14.1 with `flyway-core`, `flyway-mysql`, and Boot 4 `spring-boot-starter-flyway` is used for schema migration.
- JJWT 0.12.6 is used for signed bearer credentials. Lombok is resolved as 1.18.48.
- MySQL 9.6.0 is the local service. Migration V1 ran successfully, although Flyway warns that 9.6 is newer than its verified MySQL version (8.1).
- The Vue app uses Vue 3.5.46, Vue Router 4, Element Plus 2.14.8, Axios 1.13.x, ECharts 6, and Vite 5.4.21. The initial interface does not yet use Router or ECharts.
- POI 5.4.1, Tencent COS Java SDK 5.6.240, and springdoc OpenAPI 3.0.1 are present in the Maven build; no upload/import/export behavior is implemented yet.
- TwelveMonkeys ImageIO WebP plugin 3.15.3 is added for actual WebP decoding because the JDK ImageIO readers do not provide a built-in WebP decoder. Maven test execution loaded the plugin and decoded the checked-in test fixture on Java 23; the same validator decodes JPEG/PNG/GIF through ImageIO and verifies MIME against detected content.

## Evidence

- `mvn -DskipTests package` succeeded on Java 23.0.1 with Spring Boot 4.0.10.
- `mvn spring-boot:run` started the web server and loaded MyBatis-Plus 3.5.18.
- Flyway validated and applied V1 to local MySQL 9.6.0.
- `npm run build` produced a Vite production bundle.

## Follow-up

Verify remaining library behavior as each feature is implemented. Review npm audit findings when registry advisory access is available. Keep the Flyway/MySQL 9.6 verification warning visible until the selected Flyway release officially covers this server version or a separate compatibility check establishes otherwise.
