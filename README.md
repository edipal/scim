# SCIM 2.0 Playground

> Note - AI-Generated Code
>
> The entire codebase in this repository was written by AI (GitHub Copilot).
> This includes Java source code, configuration files, Dockerfiles, tests, and
> documentation. Human involvement was limited to design guidance, review, and
> acceptance.

## Disclaimer

This repository is a playground and reference implementation for a multi-tenant
SCIM 2.0 service provider. It is useful for exploration, interoperability
testing, and validator-driven development, but it should not be
treated as a finished identity platform.

Validate the behavior in your environment before relying on it. In particular,
if you run the management applications outside a local sandbox, replace all
development OAuth/OIDC settings and database credentials with your own values.

## Overview

SCIM 2.0 Playground is a Java 17 / Spring Boot multi-module project that
combines:

- a SCIM 2.0 API with workspace-scoped tenancy
- a management UI and management API for creating workspaces and bearer tokens
- a validator management UI that executes and stores SCIM compliance runs
- a reusable Groovy/Spock validator suite for RFC-driven regression testing
- local Docker Compose orchestration for the full stack
- Kustomize-based Kubernetes deployment support with CloudNativePG PostgreSQL,
  SOPS-encrypted secrets, and Cloudflare Tunnel integration

The design centers on workspace isolation. Every SCIM request is scoped to a
workspace via `/ws/{workspaceId}/scim/v2/**`; the current implementation
requires `workspaceId` to be a UUID, and every core SCIM entity is stored with
a `workspace_id` foreign key.

## What It Implements

The API currently covers the main SCIM provider behaviors you expect from a
playground service provider:

- `Users` CRUD
- `Groups` CRUD
- SCIM discovery endpoints:
  - `ServiceProviderConfig`
  - `Schemas`
  - `ResourceTypes`
- `PATCH` support, including filtered multi-valued operations
- `Bulk` request handling
- filtering, sorting, and pagination
- attribute projection through `attributes` and `excludedAttributes`
- weak `ETag` handling on `PUT` and `PATCH`
- route-based compatibility mode for Microsoft-specific validator quirks
- request/response logging for SCIM traffic at the workspace level

## Architecture

### Runtime modules

| Module | Role | Port | Notes |
| --- | --- | --- | --- |
| `scim-server-common` | Shared JPA entities, repositories, and common security support | n/a | Imported by API and management modules |
| `scim-server-api` | SCIM 2.0 provider API | `8080` | Stateless bearer-token auth per workspace |
| `scim-server-mgmt` | Thymeleaf management UI + management REST API | `8081` | Azure OIDC locally, Cloudflare Access JWT supported through the `cloudflare` profile |
| `scim-validator` | Groovy/Spock compliance suite | n/a | Builds a reusable test JAR consumed by validator-mgmt |
| `scim-validator-mgmt` | Validator execution UI + persistence | `8082` | Azure OIDC locally, Cloudflare Access JWT supported through the `cloudflare` profile |

### Request model

1. A client calls a SCIM endpoint under `/ws/{workspaceId}/scim/v2/**` using a
  workspace UUID.
2. `BearerTokenAuthFilter` extracts the workspace UUID from the path.
3. Non-UUID workspace identifiers are rejected with a SCIM `404` response.
4. The bearer token is hashed with SHA-256 and looked up through
   `WorkspaceTokenRepository.findByTokenHashAndNotRevoked(...)`.
5. If the token belongs to the resolved workspace and is not expired or
  revoked, the request is allowed through the filter chain.
6. The SCIM controllers resolve the workspace UUID from the route and pass it
  explicitly into services; there is no workspace ThreadLocal context.
7. `RequestResponseLoggingFilter` captures the request and response payloads for
   later inspection in the management UI.

### Multi-tenancy

Multi-tenancy is workspace-based rather than host-based:

- workspace identity comes from the route UUID, not from JWT claims
- the same bearer-token model works across all SCIM resources
- uniqueness constraints are scoped by workspace
- request logs and statistics are workspace-scoped

Examples:

- `scim_users`: unique by `(workspace_id, user_name)`
- `scim_groups`: unique by `(workspace_id, display_name)`

### Compatibility mode

Controllers expose both the default SCIM routes and compatibility routes:

- `/ws/{workspaceId}/scim/v2/Users`
- `/ws/{workspaceId}/scim/v2/{compat}/Users`

The currently implemented mode is `MS`, which applies Microsoft validator
compatibility tweaks in `MsScimUserMapper`.

### Deployment targets

The repository supports two main deployment shapes:

- local Docker Compose for fast end-to-end iteration
- Kubernetes via `k8s/app` and `k8s/cluster`, intended for a k3s-style setup
  with CloudNativePG, Kustomize, KSOPS, and Cloudflare Tunnel

## Management Surfaces

### `scim-server-mgmt`

The management application is not part of the SCIM specification. It exists to
operate the playground.

Key capabilities:

- create, list, inspect, and delete workspaces
- generate and revoke workspace bearer tokens
- inspect per-workspace SCIM request logs
- generate sample users, groups, and relations
- browse and manage workspace users and groups
- render a server-side management UI with Thymeleaf

Main routes:

- UI root: `/`
- Workspace UI: `/workspaces/{workspaceId}`
- Management API root: `/api/**`

Representative management API endpoints:

- `POST /api/workspaces`
- `GET /api/workspaces`
- `POST /api/workspaces/{workspaceId}/tokens`
- `GET /api/workspaces/{workspaceId}/logs`
- `POST /api/workspaces/{workspaceId}/generate/{kind}`

Supported generator kinds:

- `users`
- `groups`
- `relations`
- `all`

### `scim-validator-mgmt`

The validator management application wraps the reusable validator suite with a
web UI and database persistence.

It:

- accepts a SCIM base URL and auth token from the user
- executes the validator specs programmatically with the JUnit Platform launcher
- stores run-level metadata and per-test pass/fail details
- captures every HTTP exchange issued during the run
- allows viewing historical runs and deleting them

The run currently executes these spec groups:

- `A1_ServiceDiscoverySpec`
- `A2_SchemaValidationSpec`
- `A3_UserCrudSpec`
- `A4_PatchOperationsSpec`
- `A5_FilteringSpec`
- `A5_PaginationSpec`
- `A5_SortingSpec`
- `A6_GroupLifecycleSpec`
- `A7_BulkOperationsSpec`
- `A8_SecurityAndRobustnessSpec`
- `A9_NegativeAndEdgeCasesSpec`

### Management app authentication

The management applications support two deployment-facing authentication modes:

- `azure` profile, which is the default for manual local runs and uses
  interactive Azure OIDC login
- `cloudflare` profile, which switches the management apps to JWT resource
  server mode and validates the Cloudflare Access token from the configured
  request header, `Cf-Access-Jwt-Assertion` by default

The Docker Compose env files and the Kubernetes manifests use the `cloudflare`
profile for the management applications. Manual local runs default to `azure`
unless you explicitly set `SPRING_PROFILES_ACTIVE=cloudflare`.

## Data Model Notes

Some repository-specific implementation details matter if you extend the code:

- `ScimUser` flattens `name.*` and enterprise extension manager fields into
  columns.
- multi-valued user attributes are stored as JSON columns on `scim_users`,
  backed by list fields on `ScimUser`; Flyway
  `V2__migrate_user_collections_to_json.sql` removed the old dedicated child
  tables.
- `ScimUser` and `ScimGroup` use optimistic locking through `@Version`, which is
  surfaced as weak SCIM `ETag` values.
- group membership uses a polymorphic `memberValue` identifier, so delete flows
  must explicitly clear memberships rather than assuming a simple foreign-key
  cascade.

## Tech Stack

- Java 17
- Spring Boot 3.5.13
- Spring MVC, Spring Security, Spring Data JPA, Thymeleaf
- PostgreSQL for the main playground and validator persistence stores
- CloudNativePG for Kubernetes PostgreSQL clustering
- Groovy 4 + Spock + REST Assured for validator coverage
- JUnit Platform launcher for embedded validator execution
- Docker / Docker Compose for local orchestration
- Kustomize + KSOPS + SOPS + age for Kubernetes manifests and secret handling
- Cloudflare Access + Cloudflare Tunnel for edge access in the Cloudflare path
- GitHub Actions for CodeQL, release automation, and Docker image publishing

## Repository Layout

```text
.
├── age/                       # age / SOPS key rotation helper and notes
├── docker/
│   └── env/                   # Compose env files for local containers
├── k8s/
│   ├── app/                   # Namespaced SCIM application stack (namespace, DB, apps)
│   └── cluster/               # Cluster support resources (storage tuning, cloudflared)
├── scim-server-api/            # SCIM API application
├── scim-server-common/         # Shared entities, repositories, and common security support
├── scim-server-mgmt/           # Management UI/API application
├── scim-validator/             # Validator specs and support classes
├── scim-validator-mgmt/        # Validator UI and persistence layer
├── test_results/               # Saved compatibility / run artifacts
├── docker-compose.yml          # Local multi-container stack
└── pom.xml                     # Root Maven reactor
```

## Getting Started

### Prerequisites

- JDK 17
- Maven 3.9+
- Docker Desktop or compatible Docker Engine for the composed stack
- PostgreSQL only if you want to run modules manually without Docker
- Microsoft Entra ID application registration if you want to use the management
  UIs with Azure OIDC
- `kubectl`, `kustomize`, `ksops`, `sops`, and an age private key if you want
  to apply the Kubernetes manifests directly from this repository
- CloudNativePG installed in the target cluster if you want to use the provided
  Kubernetes PostgreSQL manifests

### Build the reactor

```bash
mvn clean install
```

Notes:

- The validator module is part of the reactor build.
- The validator specs now self-bootstrap a disposable PostgreSQL database and
  the published `edipal/scim-server-api:latest` image through Testcontainers
  when no explicit `SCIM_*` configuration is provided.
- Use `-Dskip.validator.tests=true` if you need to skip the validator suite in a
  reactor build.

### Run with Docker Compose

This is the fastest way to boot the full playground stack locally:

```bash
docker compose up --build
```

Optional Cloudflare tunnel sidecar:

```bash
docker compose --profile cloudflare up --build
```

Default ports:

- API: `http://localhost:8080`
- Management UI: `http://localhost:8081`
- Validator UI: `http://localhost:8082`
- Playground PostgreSQL: `localhost:5432`
- Validator PostgreSQL: `localhost:5433`

The compose stack starts:

- `scim-server-api`
- `scim-server-mgmt`
- `scim-validator-mgmt`
- `postgres-playground`
- `postgres-validator`
- `cloudflared` when the `cloudflare` compose profile is enabled

Notes:

- The management containers load both their app-specific env files and
  `docker/env/cloudflare.env`.
- The checked-in env files are development helpers only. Replace all secrets,
  audience values, role-claim settings, and tunnel tokens before using them in
  a shared environment.

### Run on Kubernetes

The repository contains a Kustomize layout intended for a k3s-style cluster.

`k8s/app` deploys the SCIM application stack into the `scim` namespace:

- `namespace.yaml`
- a CloudNativePG PostgreSQL cluster plus the validator database
- `scim-server-api`
- `scim-server-mgmt`
- `scim-validator-mgmt`

`k8s/cluster` deploys supporting cluster resources:

- `local-path-storage` configuration and a custom `local-path-custom`
  `StorageClass`
- the `cloudflared` namespace and deployment

All application services are exposed internally as `ClusterIP`. External access
for the Cloudflare path is expected to come from the `cloudflared` tunnel.

Apply order:

```bash
export SOPS_AGE_KEY_FILE=~/Library/Application\ Support/sops/age/keys.txt

kustomize build --enable-alpha-plugins --enable-exec k8s/cluster | kubectl apply -f -
kustomize build --enable-alpha-plugins --enable-exec k8s/app | kubectl apply -f -
```

Notes:

- `ksops` is used as a Kustomize generator for encrypted secrets.
- The management deployments set `SPRING_PROFILES_ACTIVE=cloudflare`.
- The API deployment stays on its regular bearer-token model.
- The manifests reference published container images such as
  `edipal/scim-server-api:1.0.8`.

### Kubernetes secrets and age rotation

Secrets under `k8s/**/secrets/*.sops.yaml` are encrypted with SOPS. The root
`.sops.yaml` file defines the active age recipient, and Kustomize decrypts the
files through `ksops` at build/apply time.

To rotate the SOPS age recipient:

```bash
export SOPS_AGE_KEY_FILE=~/Library/Application\ Support/sops/age/keys.txt
python3 age/rotate_sops_age_key.py
```

The helper will generate a new age identity, update the recipient in
`.sops.yaml`, and run `sops updatekeys` across the tracked Kubernetes secret
files.

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

Validator UI:

```bash
cd scim-validator-mgmt
mvn spring-boot:run
```

### Manual environment variables

All three applications require a datasource and `ACTUATOR_API_KEY`.

Common datasource example:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/scimplayground
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export ACTUATOR_API_KEY=dev-actuator-key
```

Azure OIDC profile for management apps (default):

```bash
export AZURE_CLIENT_ID=<your-client-id>
export AZURE_CLIENT_SECRET=<your-client-secret>
export AZURE_TENANT_ID=<your-tenant-id>
export AZURE_SCOPES="openid,email,api://<app-id>/usage"
export APP_SECURITY_AZURE_ROLE_CLAIM=roles
export APP_SECURITY_OIDC_ADMIN_ROLE=admin
export APP_SECURITY_OIDC_USER_ROLE=user

# scim-server-mgmt only
export APP_SCIM_API_BASE_URL=http://localhost:8080
```

Cloudflare profile for management apps:

```bash
export SPRING_PROFILES_ACTIVE=cloudflare
export APP_SECURITY_CLOUDFLARE_ROLE_CLAIM=<claim-name>
export APP_SECURITY_OIDC_ADMIN_ROLE=admin
export APP_SECURITY_OIDC_USER_ROLE=user
export CLOUDFLARE_ACCESS_ISSUER_URI=https://<team>.cloudflareaccess.com
export CLOUDFLARE_ACCESS_AUDIENCE=<application-audience>
export CLOUDFLARE_ACCESS_JWK_SET_URI=https://<team>.cloudflareaccess.com/cdn-cgi/access/certs
export CLOUDFLARE_ACCESS_LOGOUT_URL=https://<team>.cloudflareaccess.com/cdn-cgi/access/logout
export CLOUDFLARE_ACCESS_TOKEN_HEADER=Cf-Access-Jwt-Assertion

# scim-server-mgmt only
export APP_SCIM_API_BASE_URL=http://localhost:8080
```

The `cloudflare` profile is intended for deployments behind Cloudflare Access,
or another trusted proxy that injects the configured token header.

Use `docker/env/scim-server-api.env`, `docker/env/scim-server-mgmt.env`,
`docker/env/scim-validator-mgmt.env`, and `docker/env/cloudflare.env` as shape
references only. Do not reuse those values unchanged for a shared or production
environment.

## First-Use Workflow

### 1. Start the applications

Use Docker Compose, Kubernetes, or run the modules manually.

### 2. Access the management UI

For the `azure` profile, open `http://localhost:8081` and sign in through the
configured Azure OIDC provider.

For the `cloudflare` profile, place the application behind Cloudflare Access and
let the proxy provide the access JWT header expected by the application.

### 3. Create a workspace

Use the management UI or the management API to create a workspace. Each
workspace becomes an isolated SCIM tenant.

### 4. Generate a bearer token

Create a workspace token from the management UI or the management API. The raw
token is only shown once. At rest, only the SHA-256 hash is stored.

### 5. Call the SCIM API

Use the workspace UUID in the route.

Example discovery request:

```bash
export SCIM_TOKEN=<workspace-token>
export WORKSPACE_UUID=<workspace-uuid>

curl \
  -H "Authorization: Bearer ${SCIM_TOKEN}" \
  -H "Accept: application/scim+json" \
  http://localhost:8080/ws/${WORKSPACE_UUID}/scim/v2/ServiceProviderConfig
```

Example user creation:

```bash
curl \
  -X POST \
  -H "Authorization: Bearer ${SCIM_TOKEN}" \
  -H "Content-Type: application/scim+json" \
  -H "Accept: application/scim+json" \
  http://localhost:8080/ws/${WORKSPACE_UUID}/scim/v2/Users \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "alice@example.com",
    "name": {
      "givenName": "Alice",
      "familyName": "Example"
    },
    "active": true
  }'
```

### 6. Generate sample data if needed

The management API can seed test users, groups, and relationships for a
workspace, which is useful before running interoperability or pagination tests.

### 7. Run the validator

Open `http://localhost:8082`, enter the SCIM base URL and bearer token, then run
the spec suite and inspect the captured exchanges.

## SCIM Validator Usage

### Through the validator management UI

1. Open `http://localhost:8082`.
2. Pass through the configured management authentication layer.
3. Enter a run name, the SCIM base URL, and the bearer token.
4. Execute the run.
5. Review per-test results and HTTP request/response exchanges.

### Through the CLI validator module

The standalone validator module now starts its own disposable validator target
when you do not provide explicit `SCIM_*` settings. It uses Testcontainers to
run PostgreSQL plus the published `edipal/scim-server-api:latest` image, seeds a
workspace directly in PostgreSQL, inserts a matching SHA-256 token hash, and
then runs the specs against that bootstrapped tenant.

Default local execution:

```bash
cd scim-validator
mvn test
```

If you want to point the validator at an already running SCIM service instead,
pass the SCIM target via CLI properties:

```bash
cd scim-validator
mvn test \
  -Dscim.testcontainers.enabled=false \
  -Dscim.baseUrl=http://localhost:8080/ws/<workspace-uuid>/scim/v2 \
  -Dscim.authToken=<workspace-token>
```

Alternative CLI model:

```bash
cd scim-validator
mvn test \
  -Dscim.testcontainers.enabled=false \
  -Dscim.apiUrl=http://localhost:8080 \
  -Dscim.workspaceId=<workspace-uuid> \
  -Dscim.authToken=<workspace-token>
```

Environment variables remain supported as well:

```bash
export SCIM_BASE_URL=http://localhost:8080/ws/<workspace-uuid>/scim/v2
export SCIM_AUTH_TOKEN=<workspace-token>

cd scim-validator
mvn test
```

Alternative environment model:

```bash
export SCIM_API_URL=http://localhost:8080
export SCIM_WORKSPACE_ID=<workspace-uuid>
export SCIM_AUTH_TOKEN=<workspace-token>

cd scim-validator
mvn test
```

The validator will derive the full base path from `SCIM_API_URL` and
`SCIM_WORKSPACE_ID` if `SCIM_BASE_URL` is not provided. `SCIM_WORKSPACE_ID`
must be the workspace UUID used by the API route.

Mode selection:

- Default: Testcontainers bootstrap is enabled.
- Disable it explicitly with `-Dscim.testcontainers.enabled=false` or `SCIM_TESTCONTAINERS_ENABLED=false` when targeting another environment.

Advanced overrides for the automatic bootstrap:

- `SCIM_VALIDATOR_API_IMAGE` or `-Dscim.testcontainers.apiImage=...`
- `SCIM_VALIDATOR_POSTGRES_IMAGE` or `-Dscim.testcontainers.postgresImage=...`

## CI/CD and Release Automation

The repository already includes GitHub Actions workflows for:

- CodeQL analysis on pushes and pull requests targeting `main`
- manual version bump + tag + GitHub release generation
- Docker image publishing for:
  - `edipal/scim-server-api`
  - `edipal/scim-server-mgmt`
  - `edipal/scim-validator-mgmt`

Docker images are published for both `linux/amd64` and `linux/arm64`.

## Development Notes

Project-specific conventions that matter when contributing:

- no Lombok
- constructor injection throughout
- SCIM payloads are assembled as `Map<String, Object>` structures rather than
  dedicated response DTO hierarchies
- static mapper utilities are heavily used for SCIM transformations
- DTO layers in management applications make use of Java records
- transactional boundaries in services and selected controllers are deliberate
- management security is profile-driven: Azure OIDC by default, Cloudflare JWT
  resource-server mode when the `cloudflare` profile is active
- shared security helpers for the management apps live in `scim-server-common`

If you add or change a SCIM attribute, align all of the following:

1. entity model
2. mapper logic
3. patch support
4. schema metadata
5. filter and sort behavior
6. attribute projection behavior
7. validator coverage

If you change deployment or secret-handling behavior, review all of the
following:

1. `docker-compose.yml`
2. `docker/env/*.env`
3. `k8s/app/**`
4. `k8s/cluster/**`
5. `.sops.yaml`
6. `age/rotate_sops_age_key.py`

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](./CONTRIBUTING.md) for the
recommended workflow, validation checklist, and repository-specific conventions.

## Security

See [SECURITY.md](./SECURITY.md) for vulnerability reporting guidance and
operational security expectations.

## License

This project is licensed under the Apache License, Version 2.0. See
[LICENSE](./LICENSE).