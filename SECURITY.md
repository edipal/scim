# Security Policy

## Supported Versions

This project currently supports:

- the latest code on the `main` branch
- the latest published release artifacts and Docker images derived from `main`

Older branches and ad hoc local builds may still be useful for development, but
they should not be assumed to receive security fixes.

## Reporting a Vulnerability

Do not open public GitHub issues for security vulnerabilities.

Use GitHub Security Advisories for private reporting:

1. Open the repository Security tab.
2. Select Advisories.
3. Create a new draft security advisory.
4. Include affected module(s), reproduction steps, impact, and suggested
   mitigation if known.

If GitHub private reporting is unavailable, use the maintainer contact options on
the GitHub profile.

## Scope of Security Review

Security-sensitive areas in this repository include:

- workspace bearer-token generation, hashing, revocation, and expiry handling
- workspace resolution from SCIM routes
- management UI authentication and authorization through Azure OIDC
- request and response logging of SCIM traffic
- Docker and environment-based local configuration
- validator run input handling, persistence, and stored HTTP exchanges

## Operational Guidance

If you deploy this project outside a local sandbox, apply these controls first:

1. Replace all development OAuth/OIDC values and datasource credentials with
   your own.
2. Terminate traffic over HTTPS and protect the management applications behind a
   trusted identity provider.
3. Restrict access to the management and validator UIs; they are operational
   surfaces, not public endpoints.
4. Treat workspace bearer tokens as secrets. They are only shown once and are
   stored hashed at rest.
5. Rotate tokens when staff, tenants, or environments change.
6. Review SCIM request/response logging carefully before using this system with
   real personal or regulated data.
7. Use separate databases and environment configuration per environment.

## Secrets Handling

- Do not commit workspace bearer tokens.
- Do not commit production client secrets.
- Do not reuse local-development OIDC settings in shared or production
  environments.
- Assume any sample environment file values are for local sandboxing only unless
  you replaced them yourself.

## Current Mitigations

The repository currently includes these baseline controls:

- SHA-256 hashing for workspace tokens before persistence
- token revocation and optional expiry support
- workspace ownership enforcement in the SCIM bearer-token filter
- stateless API security for SCIM routes
- OIDC login for the management applications
- CodeQL scanning on pushes and pull requests targeting `main`
- pinned GitHub Actions SHAs in release and Docker workflows where configured

## Security Testing Expectations

When changing authentication, authorization, request handling, or persistence,
contributors should validate at least the relevant parts of:

- `A1_ServiceDiscoverySpec`
- `A3_UserCrudSpec`
- `A4_PatchOperationsSpec`
- `A7_BulkOperationsSpec`
- `A8_SecurityAndRobustnessSpec`
- `A9_NegativeAndEdgeCasesSpec`

If a fix affects the management applications, also verify the relevant UI/API
flow manually.