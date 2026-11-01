export type Page<T> = { content: T[]; totalElements: number; totalPages: number; number: number; size: number };
export type EventRecord = { id: string; eventType: string; payload: string | Record<string, unknown>; status: string; createdAt: string };
export type Endpoint = { id: string; url: string; name: string; description: string | null; active: boolean; createdAt: string; updatedAt: string; signingEnabled: boolean };
export type Subscription = { id: string; endpointId: string; eventType: string; retryIntervalSeconds: number; createdAt: string };
export type Delivery = { id: string; eventId: string; endpointId: string; eventType: string | null; status: string; attemptCount: number; retryIntervalSeconds: number; lastAttemptAt: string | null; lastResponseStatus: number | null; lastError: string | null; nextAttemptAt: string | null; createdAt: string; updatedAt: string };
export type Attempt = { id: string; attemptNumber: number; startedAt: string; finishedAt: string | null; responseStatus: number | null; outcome: string | null; errorDescription: string | null };
export type Dashboard = { counts: Record<string, number>; successRate: number };
