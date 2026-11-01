export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); this.name = "ApiError"; }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/backend${path}`, {
      ...init, cache: "no-store",
      headers: { Accept: "application/json", ...(init.body ? { "Content-Type": "application/json" } : {}), ...init.headers },
    });
  } catch { throw new ApiError("RelayForge API is unreachable. Check that Spring Boot is running.", 0); }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try { const body: unknown = await response.json(); if (body && typeof body === "object" && "message" in body && typeof body.message === "string") message = body.message; } catch { /* non-JSON error */ }
    throw new ApiError(message, response.status);
  }
  const body = await response.text();
  if (!body) return undefined as T;
  try { return JSON.parse(body) as T; }
  catch { return body as T; }
}

export const paths = {
  events: "/api/v1/events", event: (id: string) => `/api/v1/events/${id}`,
  eventDeliveries: (id: string) => `/api/v1/events/${id}/deliveries`,
  endpoints: "/api/v1/endpoints", endpoint: (id: string) => `/api/v1/endpoints/${id}`,
  subscriptions: (id: string) => `/api/v1/endpoints/${id}/subscriptions`,
  subscription: (id: string, type: string) => `/api/v1/endpoints/${id}/subscriptions/${encodeURIComponent(type)}`,
  deliveries: "/api/v1/deliveries", delivery: (id: string) => `/api/v1/deliveries/${id}`,
  attempts: (id: string) => `/api/v1/deliveries/${id}/attempts`, retry: (id: string) => `/api/v1/deliveries/${id}/retry`,
  summary: "/api/v1/dashboard/summary",
} as const;
