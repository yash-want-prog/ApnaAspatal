# SmartTriage

## 1. Project name

**SmartTriage** — developed inside the `ApnaAspatal` repository.

The generated Spring Boot artifact is named `portal`; this is the Maven/module name, not the product name. SmartTriage is the product.

## 2. Project purpose

SmartTriage is an AI-assisted healthcare triage and care-navigation platform. The eventual goal is to help patients understand the urgency of their symptoms and route them to the right level of care.

That is the destination, **not** the current work. Right now the project serves a second, equally important purpose: it is Yashwant's vehicle for learning Spring Boot properly, from fundamentals upward. Where product priority and learning progression disagree, **learning progression wins.**

## 3. Current learning phase

**Day 1 — Project bootstrap and database connectivity.**

Goal for this phase: get a Spring Boot application to start successfully with a working PostgreSQL connection. Nothing beyond that.

## 4. Current technology stack

| Layer | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.1 |
| Web | `spring-boot-starter-webmvc` |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) |
| Validation | `spring-boot-starter-validation` |
| Connection pool | HikariCP (bundled with the JPA starter) |
| Database | PostgreSQL |
| JDBC driver | `org.postgresql:postgresql` (runtime scope) |
| Build | Maven (via the bundled `mvnw` wrapper) |
| Dev convenience | `spring-boot-devtools` |

This list is complete. If something is not on it, it is not in the project.

## 5. Current project status

- Spring Boot project generated and importing cleanly.
- Maven build confirmed working.
- `PortalApplication.java` exists — the single generated entry point.
- `application.properties` contains only `spring.application.name=portal`.
- **The application does not start yet.** See section 10.
- No entities, repositories, services, or controllers have been written.
- No endpoints exist. `GET /api/health` is the next milestone, not yet built.

## 6. Architecture principles

- **Layered architecture:** Controller → Service → Repository → Entity. No layer reaches past its neighbour.
- **Controllers stay thin.** Business logic belongs in services, not controllers.
- **DTOs at the boundary.** Entities are not exposed directly through the API once DTOs are introduced.
- **Constructor injection only.** No field injection via `@Autowired`.
- **Clean, production-style code** — but no abstraction that does not yet earn its place. No interface with a single implementation "just in case."
- **Configuration is not code.** Environment-specific values (URLs, credentials) live in properties or environment variables, never hardcoded in Java.
- **Secrets never get committed.** Database passwords come from environment variables, especially given this project will eventually touch health data.

## 7. Learning rules (how to work with Yashwant)

These are binding. They override the default instinct to be maximally helpful by producing code.

1. **Teach before implementing.** Explain the concept first. Code comes after understanding, never before it.
2. **One step at a time.** One concept, one small piece of work per response. Do not run ahead to the next step because it seems obvious.
3. **Explain annotations and design decisions.** Every annotation that appears gets explained — what it does, why it is there, what breaks without it. The same applies to structural choices.
4. **Do not generate the whole application.** Never produce an entire feature, layer, or project in one response. Yashwant writes the code.
5. **Do not rewrite his code when he makes a mistake.** Point at the line, name the problem, and let him fix it. A corrected file handed back teaches nothing. Review; do not replace.
6. **Do not introduce advanced technologies before their planned phase.** If it is in section 8, it does not get suggested, scaffolded, or "just added quickly" — regardless of how much cleaner it would make things.

Default working loop: **explain → assign → review → refine.**

Maintain a running list of concepts learned vs. concepts still to understand, and carry it forward at the end of each session.

## 8. Intentionally NOT implemented yet

Deferred on purpose. Their absence is a decision, not an oversight, and not a gap to be helpfully filled:

- AI / ML triage logic — the headline feature, deliberately last
- Authentication and authorization (JWT, Spring Security)
- Docker and containerization
- Microservices — this is a single monolith by choice
- Redis / caching
- Kafka / messaging / event-driven anything
- Cloud deployment and CI/CD
- Flyway / Liquibase migrations
- API documentation tooling (Swagger / OpenAPI)
- Testing frameworks beyond what the starters already provide

Do not propose these unless Yashwant raises them first.

## 9. Planned development roadmap

**Phase 1 — Foundations (current)**

1. Project generation and Maven build ✅
2. PostgreSQL connection and successful application startup ⬅ **blocked, see §10**
3. `GET /api/health` — first endpoint, proves the web layer works

**Phase 2 — First real domain**

4. `Patient` entity with JPA annotations
5. `PatientRepository` via Spring Data JPA
6. Patient CRUD REST API (full Controller → Service → Repository stack)
7. Bean Validation on incoming requests
8. DTOs and a proper API boundary
9. Centralized exception handling (`@ControllerAdvice`)

**Phase 3 — Domain depth**

10. Additional entities and the relationships between them
11. Query methods, pagination, sorting

**Phase 4+ — Deferred technologies**

Everything in section 8, introduced one at a time, only once the fundamentals are solid.

## 10. Current unresolved issue — DataSource / PostgreSQL

**Status: open. This is the active blocker.**

The application fails during startup with:

```
Failed to configure a DataSource: 'url' attribute is not specified and
no embedded datasource could be configured.
Failed to determine a suitable driver class
```

**Cause:** `spring-boot-starter-data-jpa` is on the classpath, so Spring Boot auto-configuration tries to build a `DataSource` at startup. No `spring.datasource.*` properties have been set, so it has no URL — and with no URL it cannot infer which JDBC driver to load.

**Important nuance:** the PostgreSQL driver *is* present in `pom.xml`. The message is misleading. The driver is not missing; Boot simply has no URL to infer it from.

**Why it compiles but fails at startup:** compilation checks types only. `application.properties` is never compiled — it is copied verbatim to `target/classes/` and read at runtime. Configuration errors are invisible to `javac` and only surface when the ApplicationContext is built.

**The connection chain** (the failure is at the DataSource layer — nothing below it has been reached yet):

```
Java code → Spring Data JPA → JPA → Hibernate → DataSource
          → HikariCP → PostgreSQL JDBC Driver → PostgreSQL server
```

**Planned resolution — not yet applied:**

1. Verify PostgreSQL is running (`Get-Service *postgres*`)
2. Create the database (`CREATE DATABASE ...`) — Boot will not create it
3. Add `spring.datasource.url`, `.username`, `.password`
4. Add `spring.jpa.hibernate.ddl-auto` — discuss the values before choosing
5. Consider `spring.jpa.show-sql` while learning
6. Restart; success is confirmed by `HikariPool-1 - Start completed` in the logs

Open decision: hardcoded password vs. environment variable. Leaning toward the environment variable, but it is Yashwant's call.

## 11. Generated project coordinates

| Setting | Value |
|---|---|
| Group | `com.ApnaAspatal` |
| Artifact | `portal` |
| Base package | `com.ApnaAspatal.portal` |
| Java version | 17 |
| Spring Boot version | 4.1.1 |
| Main class | `com.ApnaAspatal.portal.PortalApplication` |
| Build tool | Maven (`mvnw` / `mvnw.cmd`) |

All application code lives under `com.ApnaAspatal.portal`. Note the non-standard capitalisation of `ApnaAspatal` in the package name — it is unconventional for Java, but it is what was generated, and renaming it is not a Day 1 concern.
