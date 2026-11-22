# Slice 05 — Build, test and CI

## Goal

Make the implementation verifiable. Every business rule has a test at the narrowest useful
layer. The build produces a coverage report that Sonar can read.

## Toolchain

| Item | Value |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.3 |
| Build | Maven, through `./mvnw` |
| Test framework | JUnit 5, AssertJ, Spring Boot test slices |
| Coverage | JaCoCo 0.8.16 |
| Static analysis | SonarCloud, Sonar Maven plugin 5.9.0.7382 |
| Database | H2 in-memory |

## Local commands

```bash
# Run the tests
./mvnw test

# Full verification with coverage
./mvnw -B -ntp clean verify -Pcoverage

# Run the service
./mvnw spring-boot:run
```

The `coverage` profile runs JaCoCo `prepare-agent` and `report`. It writes
`target/site/jacoco/jacoco.xml`, which is the path Sonar reads.

## Test layers

The project uses the narrowest layer that can prove a rule.

| Layer | Annotation | What it proves |
| --- | --- | --- |
| Domain | Plain JUnit | Money rules, claim-number format, schedule math, allocation, credit, status. |
| Persistence | `@DataJpaTest` | Mapping, DDL, constraints, cascade, ordering, repository queries. |
| Service | Plain JUnit with mocked repositories | Orchestration and error mapping that the web layer does not cover. |
| Web | `@WebMvcTest` with `@MockitoBean` | Routes, JSON field names, status codes, `ProblemDetail`, `Location` headers. |
| Full context | `@SpringBootTest` + `@AutoConfigureMockMvc` | Wiring and a small number of real business flows. |
| Concurrency | `@SpringBootTest` with two threads | The pessimistic lock prevents double allocation and double refund. |

Web tests mock the service, not the controller or the advice. Repository tests do not mock the
repository. Domain tests use a fixed `Clock`, so no test depends on the wall clock.

## Current verification

The final local run of `./mvnw -B -ntp clean verify -Pcoverage`:

| Metric | Result |
| --- | --- |
| Tests | 220 passed, 0 failures, 0 errors, 0 skipped |
| Line coverage | 92.2% |
| Branch coverage | 82.4% |
| Instruction coverage | 91.0% |
| Classes analysed | 45 |

Sonar excludes `**/config/**`, `**/*Application.java` and `**/dto/*.java` from coverage.
JaCoCo does not use those exclusions. The two numbers therefore measure slightly different code
sets.

The intentional `500` test logs a stack trace. That output is expected: the test proves the
handler logs the cause and returns a generic body.

## Test database

`src/test/resources/application-test.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true
```

- The unique database name prevents context caches from sharing one H2 database.
- `create-drop` gives each context a clean schema.
- `halt_on_error=true` turns a broken DDL into a build failure.
- `open-in-view=false` prevents accidental lazy loading during response rendering.

The main application profile still uses the Boot default for open-in-view. The test profile is
stricter.

## GitHub Actions

### Build verification

`.github/workflows/ci.yml` runs on pushes to `main` and on pull requests:

```bash
./mvnw clean verify -DskipTests
```

This is a compile and package check. It does not run the tests.

### SonarQube

`.github/workflows/sonar.yml` runs on pushes to `main` and on pull requests:

```bash
./mvnw -B verify sonar:sonar -Pcoverage   -Dsonar.organization=snobo   -Dsonar.projectKey=DevSnobo_rate-payment-service-case-study
```

It uses `SONAR_TOKEN`, then uploads `target/surefire-reports/` and `target/site/jacoco/` as
build artefacts. This is the workflow that actually executes the 220 tests.

Both workflows:

- use the Maven wrapper;
- request `contents: read`;
- cancel superseded runs on the same ref through a concurrency group;
- pin each action by commit SHA.

## Verification gaps

The current setup is honest but not complete for a production repository:

- In CI, tests run only in the Sonar workflow. A failing test does not fail the build workflow,
  because that workflow skips tests.
- `sonar.qualitygate.wait=true` is not set, so Sonar analysis can succeed when the quality gate
  fails.
- Fork pull requests do not receive `SONAR_TOKEN`; the Sonar workflow needs a guard or a separate
  path for forks.
- There is no Maven Enforcer rule for the Java and Maven versions.
- There is no Dependabot or Renovate configuration for Maven and GitHub Actions updates.
- There is no `timeout-minutes` on the jobs.
- H2 and generated schema are acceptable for the case study, not for production.

## Related code

- [`pom.xml`](../../pom.xml)
- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
- [`.github/workflows/sonar.yml`](../../.github/workflows/sonar.yml)
- [`application-test.properties`](../../src/test/resources/application-test.properties)
