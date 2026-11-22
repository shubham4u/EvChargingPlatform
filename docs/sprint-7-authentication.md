# Sprint 7 — Authentication and Authorization

> Status: **done**. Written after the work. Builds on Sprint 6 (merged as PR #10).

## Goal

Close the biggest gap from Sprint 6: anyone could act as any user by putting their `userId` in a request body. After this sprint, every call is made as a signed-in account, the API takes the caller's identity from the token, and each person can only touch what they are allowed to.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Who handles accounts and passwords | **Keycloak 26** in Docker Compose, realm `ev-charging` | `architecture.md` targets OAuth 2.1 / OIDC. The API never sees a password. |
| How the API checks callers | Spring Security **OAuth2 resource server**, stateless, JWT | Checks signature (JWKS), expiry, **issuer** and **audience** (`ev-platform-api`), with no call to Keycloak per request |
| Where the user id comes from | The token's `sub`. `userId`/`ownerUserId` were removed from request bodies, and ignored if sent. | The body is caller-controlled; the token is signed |
| How the profile links to the login | `User.id` = Keycloak `sub`. `POST /users/me` creates the profile from the token (id, email, name). | One id everywhere; no mapping table |
| Roles | Keycloak realm roles. Every account gets `driver` (default role); `operator` is assigned. | Operators manage stations and can act on any driver's resources |
| Where ownership is checked | Web adapters, via `AuthenticatedUser.requireOwnerOrOperator(ownerId)`, after loading the resource | Keeps domain and application services free of security types. Coarse role rules sit in one place (`SecurityConfig`). |
| Status for someone else's resource | **403** (not 404) | Clearer while learning. Returning 404 would hide that the id exists; worth revisiting for production. |
| Secrets | No passwords in git. The realm file has roles and clients only. The admin password comes from `.env`; scripts create throwaway users with random passwords. | Same rule as `POSTGRES_PASSWORD` |
| How scripts get tokens | Direct access grant (username + password) on the public `ev-cli` client | Dev-only convenience. Browser or mobile apps should use authorization code + PKCE on the same client (already enabled). |

### Docker networking and the issuer

Tokens are requested from your machine at `http://localhost:8081`, but the API runs inside Docker, where Keycloak is `http://keycloak:8080`. Two settings let both work:
- `KC_HOSTNAME=http://localhost:8081` makes every token's `iss` the public URL, whichever address requested it, and the API checks `iss` against that URL.
- `OIDC_JWK_SET_URI=http://keycloak:8080/...` lets the API fetch signing keys over the compose network.

Spring Boot validates the issuer from `issuer-uri` while using `jwk-set-uri` for keys, so no discovery call to `localhost` is made from inside the container.

## Who can do what

| Endpoint | Anonymous | Driver | Operator |
|---|---|---|---|
| Health, `GET /stations…` (incl. connectors) | ✅ | ✅ | ✅ |
| `POST /stations`, `POST /stations/{id}/connectors` | 401 | 403 | ✅ |
| `POST/GET /users/me` | 401 | self | self |
| `GET /users` | 401 | 403 | ✅ |
| Vehicles, reservations, charging sessions | 401 | own only (lists show own) | all |

## What changed

- **New:** `shared/security/SecurityConfig` (filter chain, problem-detail 401/403), `KeycloakRealmRoleConverter` (`realm_access.roles` → `ROLE_*`), `AuthenticatedUser`.
- **Identity:** `POST /users` (anonymous, any email) was replaced by `POST /users/me` and `GET /users/me`. A second profile for the same account → `409 User already registered`. The "user not found" message now says to call `POST /users/me`.
- **Vehicles, reservations, charging:** the caller is the owner/user. Lists return the caller's own items (operators: all). Reads and state changes check ownership. Added `findByUserId` queries for reservations and sessions.
- **Infra:**
  - `keycloak/realm-ev-charging.json`
  - a `keycloak` service with a readiness healthcheck; the API waits for it
  - `KEYCLOAK_ADMIN_PASSWORD` in `.env.example`
  - OIDC settings in `application.yml` and the k8s ConfigMap
- **Scripts:** `scripts/lib/Keycloak.ps1` (create user, assign role, get token, decode token), `new-user.ps1`, `get-token.ps1`, and a rewritten `demo-flow.ps1`.
- **Docs:** `api-design.md` (auth section and access table), `openapi.yaml` (bearer scheme; 401/403 on 18 secured operations; 3 public), `api-smoke-test.md`, `README.md`.
- **Dependencies:** `spring-boot-starter-oauth2-resource-server`, `spring-security-test` (test). Both versions are managed by Spring Boot.

## Bugs found while verifying against the real stack

1. **Default role not applied.** Keycloak's realm import ignores composites given under `defaultRole`, so new accounts had no `driver` role. It was caught by decoding a real token, because the mocked tests can't see it. Fixed by declaring the composites on the `default-roles-ev-charging` role itself, and confirmed with fresh accounts: drivers get `driver`, operators get `driver` and `operator`.
2. **`POST /users/me` returned 415 from PowerShell.** Windows PowerShell's `Invoke-RestMethod -Method Post` with no body sends `Content-Type: application/x-www-form-urlencoded`. The first fix, adding a second mapping, made Spring treat a body-less request as ambiguous: it matched both mappings, threw `IllegalStateException`, and our handler turned that into 409. Final fix: the JSON mapping requires its body, and a separate mapping handles body-less POSTs. Verified with all four client styles: PowerShell with no body, PowerShell with `{}`, PowerShell with a display name, and `curl` with no body.
3. **k8s manifest was invalid (pre-existing).** It had no `---` between the ConfigMap and the Deployment. Fixed; it now parses as three documents.

## Tests

**169 tests, 0 failures, 0 errors — BUILD SUCCESS** (145 → 169).

- Every `@WebMvcTest` now imports the real `SecurityConfig` and calls as a token via `support/TestJwt` (`driver(id)` / `operator(id)`, with Keycloak-shaped claims).
- New cases cover:
  - 401 without a token
  - 403 for drivers on operator endpoints
  - 403 on other drivers' vehicles, reservations, sessions and profiles
  - operators acting on anyone's resources
  - body `userId` being ignored
  - lists scoped to the caller
- `KeycloakTokenMappingTest` (5) covers role mapping, the name fallback, and the ownership guard.
- `UserApplicationServiceTest` covers a second profile and an account without an email.

## End-to-end verification

`docker compose up --build -d` on 2026-11-22: Keycloak healthy, API UP, 0 `ERROR` lines at startup. `scripts/demo-flow.ps1` passed **all 31 checks**:

```
== 0. Sign up three accounts in Keycloak       [PASS] Alice's token is for the API and carries the driver role
== 1. Anonymous and forged callers             [PASS] registering a car without a token -> 401 Authentication required
                                               [PASS] a token with a forged signature -> 401 Authentication required
== 2. Profile from login                       [PASS] profile id is her Keycloak account id
                                               [PASS] registering a second profile -> 409 User already registered
== 3. Her EV                                   [PASS] Bob adding a car before creating his profile -> 404 User not found
                                               [PASS] Bob reading Alice's car -> 403 Forbidden
== 4. Stations are operator-only               [PASS] Alice creating a station -> 403 Forbidden
== 5. Reserve                                  [PASS] Bob reading / cancelling Alice's reservation -> 403 Forbidden
== 6–8. Charge                                 [PASS] Bob stopping Alice's session -> 403 Forbidden
                                               [PASS] Bob sees only his own session
== 10. Operator                                [PASS] Olivia stops Bob's session; Alice listing all users -> 403
All 31 checks passed.
```

Token validation was also checked with genuinely signed tokens that should still be refused:

| Token | Result |
|---|---|
| `ev-cli` token, correct realm and audience | accepted (404: no profile yet) |
| Same realm, `admin-cli` client (no `ev-platform-api` audience) | **401** |
| `master` realm (wrong issuer) | **401** |

Every manual step in `docs/api-smoke-test.md` was run as written, with a unique VIN and station reference substituted.

## Open items

1. **Reservation ↔ session lifecycle** (carried over): starting a session doesn't activate or complete the reservation.
2. **Race conditions** (carried over): the connector, vehicle and overlap checks are check-then-insert; this needs partial unique indexes in Postgres.
3. **Browser login.** There is no front end yet, so nothing uses the authorization code + PKCE flow. Once one exists, disable direct access grants on `ev-cli` outside dev.
4. **Profile sync.** If a user changes their email in Keycloak, the profile keeps the old one. Either refresh it on `GET /users/me` or consume Keycloak admin events.
5. **Production Keycloak.** `start-dev` uses a file database and HTTP. Production needs `start` with Postgres, TLS, and a real hostname (k8s has a placeholder `OIDC_ISSUER_URI`).
6. **Search, pricing, payment**, per `architecture.md`.
7. **Events published before commit** (carried over): needs an outbox.
