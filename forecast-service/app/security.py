"""
app/security.py

forecast-service's equivalent of every Java service's
GatewayHeaderAuthenticationFilter (see fleet-service, schedule-service,
maintenance-service, alert-service, report-service, approver-service — all
five implement the identical contract, this is the Python/FastAPI version
of the same thing, not a new design).

Every request is expected to arrive through api-gateway, which has already
validated the caller's JWT (see api-gateway's JwtAuthenticationFilter) and
forwards the identity as trusted headers:

    X-User-Id    - the authenticated caller's id (a username, or a service
                   identity like "forecast-service"/"schedule-service" for
                   trusted service-to-service calls)
    X-User-Role  - one of OC (Operations Controller), MDS (Maintenance
                   Supervisor), SADA (System Admin), or SYSTEM for trusted
                   service-to-service calls made directly (outside the
                   gateway) between backend services — same four values
                   every Java service's VALID_ROLES already recognises.

Defense in depth: if GATEWAY_INTERNAL_SECRET is set (non-empty), the same
shared secret api-gateway forwards as X-Gateway-Secret must be present and
correct, so someone who reaches forecast-service directly (bypassing the
gateway) can't just forge the identity headers by hand. Left unset in
local/dev, exactly like every Java service's *.security.gateway-secret
defaults to blank there too — this must match whatever value
GATEWAY_INTERNAL_SECRET / gateway.internal-secret is set to on api-gateway
in any real environment.

/health is deliberately exempt, matching the Java services' "/actuator/"
PUBLIC_PATH_PREFIXES exemption — it's used for container/process health
checks that never carry a JWT.
"""

import logging
import os

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

log = logging.getLogger("forecast-service.security")

VALID_ROLES = {"OC", "MDS", "SADA", "SYSTEM"}

GATEWAY_INTERNAL_SECRET = os.environ.get("GATEWAY_INTERNAL_SECRET", "")

PUBLIC_PATH_PREFIXES = ("/health", "/docs", "/openapi.json", "/redoc")

def _reject(message: str) -> JSONResponse:
    return JSONResponse(
        status_code=401,
        content={"status": 401, "error": "Unauthorized", "message": message},
    )

class GatewayHeaderAuthenticationMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        if path.startswith(tuple(PUBLIC_PATH_PREFIXES)):
            return await call_next(request)

        if GATEWAY_INTERNAL_SECRET:
            presented = request.headers.get("X-Gateway-Secret")
            if presented != GATEWAY_INTERNAL_SECRET:
                log.warning(
                    "Rejected request to %s - missing/incorrect X-Gateway-Secret "
                    "(request did not come through api-gateway)", path,
                )
                return _reject("Missing or incorrect gateway secret")

        role = request.headers.get("X-User-Role")
        if role is None or role.upper() not in VALID_ROLES:
            log.warning("Rejected request to %s - missing/invalid X-User-Role", path)
            return _reject("Missing or invalid role")

        request.state.user_id = request.headers.get("X-User-Id", "unknown")
        request.state.user_role = role.upper()

        return await call_next(request)
