# Render-agent authentication

Render agents require two independent factors: an allowlisted Minecraft account
UUID and a server-issued 256-bit secret. Issuing a credential binds those two
factors together.

## Operator commands

- `/icyou render-agent issue <player-uuid>` creates a credential and displays its
  token once.
- `/icyou render-agent revoke <credential-id>` revokes one credential.
- `/icyou render-agent revoke-all <player-uuid>` revokes every credential for one
  Minecraft account.

Only permission-level-2 operators can run these commands. Keep the issued token
private. The world save stores only `SHA-256(secret)`, never the token or raw
secret.

## Connection behavior

The v1 flow is documented in `RENDER_PROTOCOL.md`. Each challenge contains 32
fresh random bytes, expires after 15 seconds, and is consumed by the first proof
attempt whether it succeeds or fails. A proof is accepted only when both the
connecting Minecraft UUID and credential ID match the saved credential.

Successful sessions are transient and bound to the current Minecraft connection.
Authenticated agents are forced into spectator mode and excluded from in-world
screen demand and genuine-player counts. Disconnect, server shutdown, failed
reauthentication, or credential revocation removes the session. Revocation also
disconnects an affected online agent immediately.
