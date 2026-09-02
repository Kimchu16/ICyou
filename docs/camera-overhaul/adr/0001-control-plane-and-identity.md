# ADR 0001: Server control plane and stable device identity

Status: accepted

## Decision

Use a logical-server control plane with a server-global persistent registry.
Represent every placed camera, terminal, and screen by an immutable reference
containing a stable UUID, a world dimension key, and a block position. Persisted
links and protocol commands use UUID identity; dimension and position are
validated location metadata, not identity.

Keep web hosting behind an `EmbeddedWebGateway` interface, media production
behind versioned render jobs, and simulation behind reference-counted chunk
leases. None of those services may infer demand from a loaded camera chunk or a
render agent.

## Consequences

- Moving/restoring a device preserves its UUID while changing location.
- Equal positions in different dimensions cannot collide.
- PR 2 must make the registry server-global before PR 3 migrates consumers.
- Legacy position-only data needs the explicit PR 4 migration and report.
- Cross-dimension references are valid before cross-dimension rendering is.
- Existing 0.2.0 consumers remain untouched until the identity types and global
  registry are independently tested.
