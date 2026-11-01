# RelayForge Console

At startup, the console asks for a workspace name, API server URL, and optional API key. It checks `GET /api/v1/dashboard/summary` before opening the dashboard. The URL and key are held in the Next.js server process for the browser session; API requests are forwarded through the same-origin `/backend/*` proxy. Restarting the Next.js process clears this in-memory configuration, so reconnect the environment after a restart. Keys are sent as `X-API-Key` and `Authorization: Bearer` headers.

Subscriptions accept `retryIntervalSeconds` (1–604800; defaults to 60). Retry delay is fixed per subscription and copied onto each delivery when its event is created, so editing a subscription affects only future deliveries. Retryable failures use that delay between attempts; the delivery worker still polls its queue every five seconds and allows five total attempts by default.

A dark, responsive developer console for the existing Spring Boot RelayForge API. Built with Next.js App Router, TypeScript, Tailwind CSS v4 and the shadcn component system, with Motion, TanStack Query, React Hook Form/Zod, Sonner, Lucide, and Recharts.

## Start locally

1. From the repository root, start PostgreSQL and the API: `docker compose up -d postgres pgadmin`, then `./mvnw spring-boot:run`.
2. In `www/`, install dependencies with `npm install`.
3. Copy `.env.example` to `.env.local` if you need to override the backend URL. `BACKEND_API_URL` defaults to `http://localhost:8080`.
4. Run `npm run dev` and open `http://localhost:3000`.

For a production build, run `npm run build`, then `npm run start`. The build uses Next.js's Webpack backend for a self-contained build in restricted local environments. Run `npm run typecheck` and `npm run lint` for static checks.

The browser calls `/backend/...`; the Next.js rewrite forwards requests to `BACKEND_API_URL`. The backend URL stays server-side and is not exposed through a `NEXT_PUBLIC_` variable. Operational queries use `cache: "no-store"` and TanStack Query controls refresh and mutations.

## Pages

- `/` dashboard with live summary counts, delivery status distribution, recent event rows, and shortcuts.
- `/events`, `/events/[id]` list and inspect accepted events and their persisted delivery jobs.
- `/endpoints`, `/endpoints/[id]` register destinations, toggle active state, manage subscriptions, and inspect deliveries.
- `/subscriptions` browse exact event type routing and add/remove subscriptions.
- `/deliveries`, `/deliveries/[id]` filter jobs, inspect status/attempt history, and manually retry failed jobs.
- `/playground` create real events using JSON templates and follow delivery state.

The backend has no daily event/delivery time-series, recent endpoint registration feed, or dedicated endpoint-delivery-history API. The UI labels those gaps rather than inventing metrics. Delivery list API currently does not offer date-range or text query filters.

Management APIs are unauthenticated in this local-development milestone. Do not expose the frontend/backend publicly without authentication and appropriate deployment security.
