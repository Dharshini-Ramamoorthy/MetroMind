export const API_BASE = (typeof import.meta !== "undefined" && import.meta.env?.VITE_API_BASE_URL)
  ? import.meta.env.VITE_API_BASE_URL.replace(/\/$/, "")
  : "https://fc3c2734c6ace950-171-79-56-101.serveousercontent.com";

export const AUTH_API_BASE = `${API_BASE}/api/v1/auth`;
export const FLEET_API_BASE = `${API_BASE}/api/v1/fleet`;
export const SCHEDULE_API_BASE = `${API_BASE}/api/v1/schedule`;
export const MAINTENANCE_API_BASE = `${API_BASE}/api/v1/maintenance`;
export const ALERT_API_BASE = `${API_BASE}/api/v1/alerts`;
export const REPORT_API_BASE = `${API_BASE}/api/v1/reports`;
export const APPROVER_API_BASE = `${API_BASE}/api/approver`;
export const USERS_API_BASE = `${API_BASE}/api/v1/users`;
