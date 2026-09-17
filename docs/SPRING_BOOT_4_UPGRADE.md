# Spring Boot 4.0.6 upgrade

## Dependency and source changes

- Upgrade the Spring Boot parent from 3.3.5 to 4.0.6. Keep Java 21 as the compilation and Docker runtime version.
- Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc` and the general test starter with `spring-boot-starter-webmvc-test` for Boot 4's modular web/test support.
- Upgrade `springdoc-openapi-starter-webmvc-ui` from 2.6.0 to 3.0.3, the Springdoc line compatible with Boot 4.0.x.
- Rename the AOP starter to `spring-boot-starter-aspectj` for the database repository interceptor.
- Explicitly retain OpenAPI 3.0 output with `springdoc.api-docs.version=OPENAPI_3_0`; the newer library otherwise defaults to 3.1.
- Add the runtime `spring-boot-h2console` module. Boot 4 separates console auto-configuration from the core modules. The console remains enabled locally and disabled by Docker Compose.
- Let Boot manage Lombok's version for both compilation and annotation processing instead of overriding it with 1.18.42.
- Migrate integration-test JSON imports from `com.fasterxml.jackson.databind` to `tools.jackson.databind` for Jackson 3, and use Boot 4's new `AutoConfigureMockMvc` package.

## Test and build changes

- Correct the rate-limit integration test's package to match its directory.
- Freeze `Instant.now()` on the synchronous MockMvc test thread. This removes a pre-existing failure caused by requests falling into different seconds, without changing the production filter. Also check that the next second restores the request budget.
- Add regression checks for Swagger UI, the OpenAPI endpoints and bearer security scheme, unknown JSON fields, and missing required fields. The unknown-field test also exercises Belgium registration.
- Run `mvn verify` during the Docker image build instead of skipping tests. Cache Maven downloads with BuildKit across image builds.

## Compatibility check

An intermediate build used Boot 3.5.16 and Springdoc 2.8.17 before moving to Boot 4.0.6. It ran 110 tests: 109 passed and the original timing-dependent rate-limit test failed. The same rate-limit failure occurred on the original Boot 3.3.5 baseline.

The migration keeps the existing controllers, services, entities, API routes, database configuration, Java 21 runtime, and localhost port 8085. H2 data and sessions are still in memory and are lost on container restart.

## References

- [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Springdoc compatibility matrix](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)
