import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { clearEnvironment, ENVIRONMENT_COOKIE, normalizeServerUrl, readEnvironment, saveEnvironment, type SavedEnvironment } from "@/lib/environment";

export const runtime = "nodejs";

export async function GET(request: NextRequest) {
  const environment = readEnvironment(request.cookies.get(ENVIRONMENT_COOKIE)?.value);
  if (!environment) return NextResponse.json({ configured: false });
  return NextResponse.json({ configured: true, name: environment.name, serverUrl: environment.serverUrl, hasApiKey: Boolean(environment.apiKey) });
}

export async function POST(request: NextRequest) {
  let body: unknown;
  try { body = await request.json(); } catch { return NextResponse.json({ message: "Enter a valid configuration." }, { status: 400 }); }
  if (!body || typeof body !== "object") return NextResponse.json({ message: "Enter a valid configuration." }, { status: 400 });
  const input = body as Record<string, unknown>;
  const name = typeof input.name === "string" ? input.name.trim() : "";
  const apiKey = typeof input.apiKey === "string" ? input.apiKey.trim() : "";
  if (!name || name.length > 48) return NextResponse.json({ message: "Workspace name must be between 1 and 48 characters." }, { status: 400 });
  if (apiKey.length > 2048) return NextResponse.json({ message: "The API key is too long." }, { status: 400 });
  let serverUrl: string;
  try {
    if (typeof input.serverUrl !== "string") throw new Error("Enter the server URL.");
    serverUrl = normalizeServerUrl(input.serverUrl);
  } catch (error) {
    return NextResponse.json({ message: error instanceof Error ? error.message : "Enter a valid server URL." }, { status: 400 });
  }

  const environment: SavedEnvironment = { name, serverUrl, apiKey };
  try {
    const response = await fetch(`${serverUrl}/api/v1/dashboard/summary`, {
      headers: { Accept: "application/json", ...(apiKey ? { "X-API-Key": apiKey, Authorization: `Bearer ${apiKey}` } : {}) },
      cache: "no-store",
      signal: AbortSignal.timeout(7000),
    });
    if (!response.ok) {
      const message = response.status === 401 || response.status === 403
        ? "The server rejected this API key. Check it and try again."
        : `The server responded with HTTP ${response.status}.`;
      return NextResponse.json({ message }, { status: 400 });
    }
  } catch {
    return NextResponse.json({ message: "Could not reach that server. Check the URL and make sure its API is running." }, { status: 400 });
  }

  const previousSession = request.cookies.get(ENVIRONMENT_COOKIE)?.value;
  clearEnvironment(previousSession);
  const sessionId = randomUUID();
  saveEnvironment(sessionId, environment);
  const response = NextResponse.json({ configured: true, name, serverUrl, hasApiKey: Boolean(apiKey) });
  response.cookies.set(ENVIRONMENT_COOKIE, sessionId, {
    httpOnly: true,
    sameSite: "lax",
    secure: request.nextUrl.protocol === "https:",
    path: "/",
    maxAge: 60 * 60 * 24 * 30,
  });
  return response;
}

export async function DELETE(request: NextRequest) {
  clearEnvironment(request.cookies.get(ENVIRONMENT_COOKIE)?.value);
  const response = NextResponse.json({ configured: false });
  response.cookies.delete(ENVIRONMENT_COOKIE);
  return response;
}
