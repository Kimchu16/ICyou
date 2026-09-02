# Terminal web access

Web access is off by default. Enable it in `config/icyou-web.properties`:

```properties
web.enabled=true
web.bind=127.0.0.1
web.port=8123
```

Restart the Minecraft server after changing this file.

## Create a token

The terminal owner or a server operator can run:

```text
/icyou token issue <terminal-slug> viewer
/icyou token issue <terminal-slug> owner
```

The token is shown once. A viewer token can read that terminal's page. An owner
token is reserved for owner-level routes and can also read the page. Do not share
an owner token with viewers.

Send the token as a bearer credential:

```text
Authorization: Bearer <token>
```

The terminal page is `GET /v1/terminals/<terminal-slug>`. Missing, invalid,
revoked, and wrong-terminal tokens all return the same not-found response.

## Viewer demand

A viewer opens demand for a camera with:

```text
POST /v1/terminals/<terminal-slug>/cameras/<camera-uuid>/demand
```

The response contains a `sessionId`. Renew it at least once every 30 seconds:

```text
PUT /v1/terminals/<terminal-slug>/cameras/<camera-uuid>/demand/<session-id>
```

Close it when viewing ends:

```text
DELETE /v1/terminals/<terminal-slug>/cameras/<camera-uuid>/demand/<session-id>
```

All three requests use the same bearer token. If renewal stops, demand expires
after 30 seconds. Revoking the token removes its demand immediately.

## Revoke access

Each issued token also shows a credential ID:

```text
/icyou token revoke <terminal-slug> <credential-id>
/icyou token revoke-all <terminal-slug> viewer
/icyou token revoke-all <terminal-slug> owner
```

Revocation takes effect immediately. Only token digests are written to the world
save; plaintext tokens are never persisted by ICyou.
