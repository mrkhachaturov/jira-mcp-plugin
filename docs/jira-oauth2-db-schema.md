# Jira OAuth 2.0 Database Schema

Findings from inspecting Jira Data Center 10.x PostgreSQL database (`at_bpm`).

Jira stores OAuth 2.0 state in Active Objects tables with prefix `AO_FE1BC5_`.

## Tables

### AO_FE1BC5_CLIENT

Application Link OAuth 2.0 clients.

| Column | Type | Notes |
|---|---|---|
| ID | varchar(255) PK | Internal ID |
| CLIENT_ID | varchar(255) UNIQUE | OAuth client_id |
| CLIENT_SECRET | varchar(255) UNIQUE | OAuth client_secret |
| NAME | varchar(255) UNIQUE | Application Link name |
| SCOPE | varchar(255) | Granted scopes |
| USER_KEY | varchar(255) | Owner user |

### AO_FE1BC5_ACCESS_TOKEN

Issued access tokens.

| Column | Type | Notes |
|---|---|---|
| ID | varchar(255) PK | Token value (SHA-256 hash) |
| CLIENT_ID | varchar(255) | Application Link client |
| USER_KEY | varchar(255) | Authenticated user |
| SCOPE | varchar(255) | Granted scopes |
| AUTHORIZATION_CODE | varchar(255) | Original auth code |
| AUTHORIZATION_DATE | bigint | When authorized (epoch ms) |
| CREATED_AT | bigint | When token issued (epoch ms) |
| LAST_ACCESSED | bigint | Last use timestamp (epoch ms) |

**No `EXPIRES_AT` column.** The `expires_in: 7200` in API responses is computed at runtime (2 hours from `CREATED_AT`). The `LAST_ACCESSED` field may also factor into expiry decisions.

### AO_FE1BC5_REFRESH_TOKEN

Issued refresh tokens.

| Column | Type | Notes |
|---|---|---|
| ID | varchar(255) PK | Token value (SHA-256 hash) |
| ACCESS_TOKEN_ID | varchar(255) | Associated access token |
| CLIENT_ID | varchar(255) | Application Link client |
| USER_KEY | varchar(255) | Authenticated user |
| SCOPE | varchar(255) | Granted scopes |
| AUTHORIZATION_CODE | varchar(255) | Original auth code |
| AUTHORIZATION_DATE | bigint | When authorized (epoch ms) |
| CREATED_AT | bigint | When token issued (epoch ms) |
| REFRESH_COUNT | integer | Number of times refreshed |

**No `EXPIRES_AT` column.** Refresh tokens never expire in the database. They persist until:
- Rotated (Jira invalidates old token and issues new one on each refresh)
- Revoked (admin removes the Application Link or user access)
- Manual DB cleanup

### AO_FE1BC5_AUTHORIZATION

Authorization grants.

### AO_FE1BC5_REDIRECT_URI

Registered redirect URIs for clients.

## Key Findings

1. **Refresh tokens are immortal** -- no TTL, no expiry column, no scheduled cleanup
2. **Rotation is enforced** -- each refresh invalidates the old token and issues a new one (per Jira docs: "Invalidates the existing access_token and refresh_token, sends new tokens in the response")
3. **Access token expiry is runtime-computed** -- `expires_in: 7200` (2 hours) is not stored, calculated from `CREATED_AT`
4. **Tokens survive Jira restarts** -- stored in PostgreSQL via Active Objects, not in-memory
5. **No auto-cleanup** -- old tokens accumulate (15 tokens found from testing, all retained)

## Implications for MCP OAuth Proxy

Our OAuth proxy maintains an in-memory mapping (proxy refresh token -> Jira refresh token). This mapping is lost on plugin restart, forcing re-authentication even though Jira's refresh token is still valid in the database.

**Pass-through approach:** Instead of maintaining a proxy mapping, pass Jira's actual refresh token through to the MCP client (same as we already do with access tokens). This eliminates the persistence problem entirely -- Jira's database becomes the single source of truth.
