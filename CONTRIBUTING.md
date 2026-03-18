# Contributing

Thanks for contributing to SCIM 2.0 Playground.

This repository is small enough to move quickly, but large enough that changes
can easily cross module boundaries. Keep contributions focused and grounded in
observable behavior.

## Ground Rules

- Be respectful and specific.
- Keep changes narrow and intentional.
- Prefer readable code over clever abstractions.
- Do not include secrets, bearer tokens, client secrets, or tenant-specific
  sensitive configuration in commits, issues, or pull requests.
- Do not mix drive-by refactors into feature or bug-fix pull requests.

## Before You Start

1. Check open issues and pull requests to avoid duplicated work.
2. For significant feature work or larger refactors, open an issue first.
3. Create your branch from `main` unless a maintainer asks otherwise.
4. Read the root `README.md` before touching runtime or validator behavior.

## Local Setup

### Prerequisites

- JDK 17
- Maven 3.9+
- Docker if you want the full local stack
- PostgreSQL if running modules outside Docker
- Azure OIDC configuration if you want to use the management UIs

### Build the full reactor

```bash
mvn clean install
```

### Run the stack with Docker

```bash
docker compose up --build
```

### Run modules manually

API:

```bash
cd scim-server-api
mvn spring-boot:run
```

Management UI/API:

```bash
cd scim-server-mgmt
mvn spring-boot:run
```

Validator management UI:

```bash
cd scim-validator-mgmt
mvn spring-boot:run
```

### Local auth shortcut for the management apps

For local development, both management modules support a `local` Spring profile
that replaces Azure OIDC with a built-in form-login setup.

Available accounts:

- `local-admin` / `local-admin`
- `local-user-1` / `local-user-1`
- `local-user-2` / `local-user-2`

Examples:

```bash
cd scim-server-mgmt
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

```bash
cd scim-validator-mgmt
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### Validator CLI execution

The validator specs are skipped by default during regular builds. To execute
them explicitly:

```bash
export SCIM_BASE_URL=http://localhost:8080/ws/<workspace-id-or-name>/scim/v2
export SCIM_AUTH_TOKEN=<workspace-token>

cd scim-validator
mvn test -Pvalidator-tests
```

## Project Conventions

These conventions are already established in the codebase. Follow them unless a
maintainer explicitly wants a refactor.

- No Lombok.
- Prefer constructor injection over field injection.
- SCIM response payloads are map-built using `Map<String, Object>` and
  `LinkedHashMap`.
- SCIM mapping code uses static mapper utilities such as `ScimUserMapper`,
  `ScimGroupMapper`, and `MsScimUserMapper`.
- Management and validator DTOs may use Java records.
- Existing transactional boundaries are intentional; do not move them casually.
- SCIM controller pagination is 1-based and capped at 200 results.
- The API is workspace-scoped; do not introduce tenant-agnostic data access for
  SCIM resources.

## Working on SCIM Behavior

If you change SCIM behavior, review impact across all of these areas:

1. API controllers for users, groups, bulk, and discovery.
2. Service-layer business logic.
3. SCIM mappers and compatibility mappers.
4. `ScimPatchEngine` and `ScimFilterParser`.
5. `ScimSchemaDefinitions` and service provider configuration flags.
6. Validator specs in `scim-validator`.
7. Management UI expectations, especially for sample-data generation and logs.

### Adding or changing a SCIM attribute

When you add or modify an attribute, update all relevant layers together:

1. JPA entity or child entity in `scim-server-model`
2. Read/write mapping in `scim-server-api`
3. PATCH support in `ScimPatchEngine` when applicable
4. Schema metadata in `ScimSchemaDefinitions`
5. Filter and sort resolution in `ScimFilterParser` when queryable or sortable
6. Projection behavior for `attributes` and `excludedAttributes`
7. Validator coverage

## Tests and Quality Checks

Run the smallest useful validation for your change before opening a pull
request.

Common checks:

```bash
mvn clean install
```

Run the validator explicitly:

```bash
cd scim-validator
mvn test -Pvalidator-tests
```

If your change affects:

- filtering or sorting, validate the corresponding A5 validator specs
- patch behavior, validate A4
- bulk behavior, validate A7
- discovery metadata, validate A1 and A2
- compatibility mode, validate the Microsoft compatibility flows you touched

## Documentation Changes

Update documentation when you change:

- runtime commands
- environment variables
- security assumptions
- route structure
- validator behavior
- project/module layout

## Pull Request Checklist

Before opening a PR, make sure it:

- has a clear title and description
- explains what changed and why
- links to an issue when relevant
- keeps unrelated cleanup out of scope
- includes documentation updates if behavior changed
- passes the relevant build and test steps
- avoids committing secrets or machine-specific noise

## Commit Messages

Short, descriptive commits are preferred. Conventional-style commits are fine,
for example:

- `feat: add microsoft compatibility route handling`
- `fix: enforce workspace token ownership in filter`
- `docs: document validator profile usage`
- `perf: reduce management user mapping overhead`

## Reporting Bugs

When opening a bug report, include:

- steps to reproduce
- expected behavior
- actual behavior
- the module or endpoint affected
- logs, payloads, or screenshots when useful
- whether the issue reproduces through the validator suite

## Suggesting Features

Open an issue describing:

- the problem being solved
- the proposed approach
- any protocol or compatibility implications
- whether the change affects RFC compliance, Microsoft compatibility mode, or
  management UX

## Security Issues

Do not report vulnerabilities through public issues.

Follow the process in `SECURITY.md` instead.