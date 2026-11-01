export const ENVIRONMENT_COOKIE = "relayforge_environment";
const environmentStore = globalThis as typeof globalThis & { relayforgeEnvironments?: Map<string, SavedEnvironment> };
const environments = environmentStore.relayforgeEnvironments ??= new Map<string, SavedEnvironment>();

export type SavedEnvironment = {
  name: string;
  serverUrl: string;
  apiKey: string;
};

export function normalizeServerUrl(input: string): string {
  const url = new URL(input.trim());
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("Use an http:// or https:// server URL.");
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error("Enter a server URL without credentials, query parameters, or a fragment.");
  }
  return url.toString().replace(/\/$/, "");
}

export function readEnvironment(sessionId?: string): SavedEnvironment | null {
  return sessionId ? environments.get(sessionId) ?? null : null;
}

export function saveEnvironment(sessionId: string, environment: SavedEnvironment) {
  environments.set(sessionId, environment);
}

export function clearEnvironment(sessionId?: string) {
  if (sessionId) environments.delete(sessionId);
}
