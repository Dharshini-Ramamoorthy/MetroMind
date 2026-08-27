# Approval Service Changes

## Decision safety
- Only APPROVED and REJECTED are accepted as decisions.
- Only PENDING tasks can be decided.
- A task is atomically claimed as PROCESSING before downstream calls, preventing concurrent double decisions.
- Unauthorized callers are checked before the task is claimed.

## Cascade reliability
- Downstream maintenance/schedule/fleet failures are no longer swallowed.
- If the downstream cascade fails, the task returns to PENDING with cascadeStatus=FAILED and the error recorded, so it can be retried.
- If downstream succeeds but final task persistence fails, the task is not reopened automatically; it is left in PROCESSING/FINALIZATION_FAILED for reconciliation to avoid executing the downstream action twice.

## Auditability
ApprovalTask now records:
- decidedAt
- decidedBy
- cascadeStatus
- cascadeError

ApprovalHistory now records:
- eventType
- cascadeStatus
- errorMessage

## CoF ownership
For CERTIFICATE_OF_FITNESS, Approval Service updates the Maintenance ticket only. Maintenance remains responsible for releasing the train to Fleet, avoiding two services independently changing Fleet state.
