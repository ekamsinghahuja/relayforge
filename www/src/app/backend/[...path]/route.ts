import { NextRequest, NextResponse } from "next/server";
import { ENVIRONMENT_COOKIE, readEnvironment } from "@/lib/environment";

export const runtime = "nodejs";
type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: RouteContext) {
  const environment = readEnvironment(request.cookies.get(ENVIRONMENT_COOKIE)?.value);
  if (!environment) return NextResponse.json({ message: "Choose a server environment first." }, { status: 401 });
  const { path } = await context.params;
  const target = new URL(`${environment.serverUrl}/${path.map(encodeURIComponent).join("/")}`);
  target.search = request.nextUrl.search;
  const headers = new Headers();
  for (const name of ["accept", "content-type", "idempotency-key"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  if (environment.apiKey) {
    headers.set("X-API-Key", environment.apiKey);
    headers.set("Authorization", `Bearer ${environment.apiKey}`);
  }
  try {
    const upstream = await fetch(target, {
      method: request.method,
      headers,
      body: ["GET", "HEAD"].includes(request.method) ? undefined : await request.arrayBuffer(),
      cache: "no-store",
      redirect: "manual",
    });
    const outputHeaders = new Headers();
    for (const name of ["content-type", "cache-control", "etag", "last-modified"]) {
      const value = upstream.headers.get(name);
      if (value) outputHeaders.set(name, value);
    }
    return new NextResponse(upstream.body, { status: upstream.status, headers: outputHeaders });
  } catch {
    return NextResponse.json({ message: "The configured RelayForge server could not be reached." }, { status: 502 });
  }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
