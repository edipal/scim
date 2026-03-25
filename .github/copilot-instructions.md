# SCIM 2.0 Playground - Copilot Instructions

## Architecture

Multi-tenant SCIM 2.0 Service Provider (RFC 7643/7644) with five Maven modules:

| Module | Role | Port |
|---|---|---|
| `scim-server-common` | Shared JPA entities, repositories, and common security utilities | - |
| `scim-server-api` | SCIM 2.0 API (Users, Groups, Discovery, Bulk) | 8080 |
| `scim-server-mgmt` | Admin UI + management API (Thymeleaf + vanilla JS) | 8081 |
| `scim-validator` | Groovy/Spock SCIM compliance suite (REST Assured) | - |
| `scim-validator-mgmt` | Validator run/inspection management service | 8082 |

Multi-tenancy is workspace-based. SCIM routes are scoped to `/ws/{workspaceId}/scim/v2/**`.

- `BearerTokenAuthFilter` resolves workspace by UUID or name from the path.
- Bearer tokens are validated via SHA-256 hash lookup (`WorkspaceTokenRepository.findByTokenHashAndNotRevoked`).
- `WorkspaceContext` (ThreadLocal) carries workspace/token for downstream services.
- All core SCIM entities are workspace-scoped with `workspace_id` foreign keys.

Compatibility mode is route-based and extensible:

- Controllers expose both base and compat route forms, for example:
	- `/ws/{workspaceId}/scim/v2/Users`
	- `/ws/{workspaceId}/scim/v2/{compat}/Users`
- `CompatMode` currently supports `MS`.
- `MsScimUserMapper` applies Microsoft validator compatibility tweaks:
	- converts selected `primary` booleans to string values
	- adds flattened enterprise manager alias key

## Build And Run

```bash
# Full reactor build
mvn clean install

# Build without SCIM validator module
mvn clean install -pl '!scim-validator'

# API local mode (requires datasource env vars and ACTUATOR_API_KEY)
cd scim-server-api && mvn spring-boot:run

# Mgmt UI/API local mode (requires datasource env vars, ACTUATOR_API_KEY, and Azure OIDC env vars)
cd scim-server-mgmt && mvn spring-boot:run

# Validator management local mode (requires datasource env vars, ACTUATOR_API_KEY, and Azure OIDC env vars)
cd scim-validator-mgmt && mvn spring-boot:run

# Docker stack (PostgreSQL 18 + API + mgmt + validator mgmt)
docker compose up --build
```

Docker default ports:

- API `:8080`
- Mgmt `:8081`
- Validator Mgmt `:8082`
- PostgreSQL `:5432`

## Validator Execution

`scim-validator` can either bootstrap its own disposable target via
Testcontainers or run against an already reachable SCIM API.

```bash
cd scim-validator && mvn test
```

Notes from `ScimBaseSpec`:

- By default, the validator can bootstrap PostgreSQL plus `edipal/scim-server-api:latest` when explicit `SCIM_*` settings are not provided.
- Disable automatic bootstrap with `SCIM_TESTCONTAINERS_ENABLED=false` or `-Dscim.testcontainers.enabled=false` when targeting an existing environment.
- You can alternatively set `SCIM_BASE_URL` (full path, including `/ws/{workspaceId}/scim/v2`).
- You can also provide `SCIM_API_URL` together with `SCIM_WORKSPACE_ID`.
- `SCIM_AUTH_TOKEN` is required for validator runs.
- `SCIM_WORKSPACE_ID` is required unless `SCIM_BASE_URL` is provided.

## Code Conventions

- No Lombok in this repository.
- Constructor injection throughout; no `@Autowired` field injection.
- SCIM response payloads are map-built (`Map<String, Object>`, usually `LinkedHashMap`), not dedicated response DTO hierarchies.
- SCIM mapping code uses static utility classes (`ScimUserMapper`, `ScimGroupMapper`, `MsScimUserMapper`).
- Java records are used in DTO layers (notably in mgmt and validator-mgmt modules).
- `@Transactional` appears on service classes and on selected controller classes/methods. Preserve existing boundaries unless intentionally refactoring.
- Content types:
	- SCIM endpoints: `application/scim+json`
	- Mgmt endpoints: standard JSON (`application/json`)

## Data Model Patterns

`ScimUser` and `ScimGroup` use optimistic locking via `@Version` (ETag support).

- Workspace-scoped uniqueness:
	- `scim_users`: `(workspace_id, user_name)`
	- `scim_groups`: `(workspace_id, display_name)`
- `ScimUser` flattens `name.*` and enterprise extension sub-attributes into columns.
- Multi-valued user attributes are dedicated child entities with `@OneToMany(cascade = ALL, orphanRemoval = true)`:
	- `emails`, `phoneNumbers`, `addresses`, `entitlements`, `roles`, `ims`, `photos`, `x509Certificates`

## Key SCIM Components

- `ScimFilterParser` (`~378` lines): recursive-descent filter parser to JPA `Specification<T>`.
	- operators: `eq ne co sw ew pr gt ge lt le`
	- logic: `and or not`, grouping with parentheses
	- supports `name.*`, `meta.*`, and enterprise extension attribute paths
- `ScimPatchEngine` (`~850` lines): RFC 7644 PATCH processing with path parsing and filtered multi-valued operations.
- `ScimSchemaDefinitions`: source of truth for discovery/schema responses.

When adding or changing attributes, keep parser, mapper, patch, and schema definitions aligned.

## Protocol And API Behavior

- SCIM pagination is 1-based (`startIndex`, `count`). Controllers clamp invalid values and enforce max count (`200`).
- Mgmt pagination input is also 1-based (`page`, `size`) and converted to Spring `PageRequest` (0-based internally).
- PUT/PATCH on Users and Groups support `If-Match` with weak ETags (`W/"<version>"`) and return `412` on mismatch.
- SCIM errors are returned with SCIM error schema and SCIM content type.

## Working In This Codebase

If you modify SCIM behavior, review impact across these areas:

1. API controllers (`Users`, `Groups`, `Bulk`, discovery endpoints)
2. Service layer logic and transactional boundaries
3. Mappers (`ScimUserMapper`, `ScimGroupMapper`, `MsScimUserMapper`)
4. Filter and patch engines
5. Schema definitions and ServiceProviderConfig flags
6. Validator specs (`A1` through `A9`) and compatibility expectations

## Adding A New SCIM Attribute

1. Extend `ScimUser`/`ScimGroup` (or add child entity in `scim-server-common` when multi-valued).
2. Update mapper read/write paths in `scim-server-api`.
3. Add PATCH support in `ScimPatchEngine` when applicable.
4. Add schema metadata in `ScimSchemaDefinitions`.
5. Extend filter/sort resolution in `ScimFilterParser` when queryable/sortable.
6. Validate ETag, projection (`attributes`/`excludedAttributes`), and compat-mode behavior.
7. Add/adjust validator coverage in relevant `scim-validator` specs.
