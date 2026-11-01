"use client";

import Link from "next/link";
import { useState } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Activity, AlertTriangle, Braces, Check, Clipboard, Code2, ExternalLink, LoaderCircle, Play, Plus, Radio, ShieldAlert, Sparkles, Waypoints } from "lucide-react";
import { useCreateEvent, useCreateSubscription, useEndpoints } from "@/hooks/use-relayforge";
import { api, paths } from "@/lib/api-client";
import type { EventRecord } from "@/types/api";
import { formatDate, shortId } from "@/lib/utils";
import { StatusBadge } from "@/components/status-badge";
import { ErrorState } from "@/components/data-states";
import { JsonViewer } from "@/components/json-viewer";

const samples: Record<string, unknown> = {
  "payment.completed": { paymentId: "pay_123", amount: 500, currency: "INR" },
  "payment.failed": { paymentId: "pay_124", reason: "insufficient_funds" },
  "order.created": { orderId: "ord_456", total: 1299.5, currency: "INR" },
  "email.requested": { to: "dev@example.com", template: "welcome" },
  "user.registered": { userId: "usr_789", email: "dev@example.com" },
};
const schema = z.object({
  eventType: z.string().trim().min(1, "Event type is required").max(100, "Use at most 100 characters"),
  payload: z.string().min(1, "JSON payload is required"),
  idempotencyKey: z.string().max(255, "Use at most 255 characters").optional(),
});
type Result = { event: EventRecord; deliveries: { id: string; endpointId: string; status: string; attemptCount: number; createdAt: string }[] };
type EnvironmentInfo = { configured: boolean; name?: string; serverUrl?: string; hasApiKey?: boolean };

function makeCurl(url: string, eventType: string, payload: string, key: string, hasApiKey: boolean) {
  let json = payload;
  try { json = JSON.stringify(JSON.parse(payload), null, 2); } catch { /* preview the user's draft as-is */ }
  const quote = (value: string) => `'${value.replaceAll("'", "'\\''")}'`;
  return [
    `curl -i -X POST ${quote(`${url}/api/v1/events`)} \\`,
    `  -H 'Content-Type: application/json' \\`,
    ...(hasApiKey ? [`  -H 'X-API-Key: YOUR_API_KEY' \\`] : []),
    ...(key.trim() ? [`  -H ${quote(`Idempotency-Key: ${key.trim()}`)} \\`] : []),
    `  -d ${quote(JSON.stringify({ eventType: eventType.trim(), payload: (() => { try { return JSON.parse(json); } catch { return {}; } })() }))}`,
  ].join("\n");
}

export default function PlaygroundPage() {
  const create = useCreateEvent();
  const createSubscription = useCreateSubscription();
  const endpoints = useEndpoints();
  const [result, setResult] = useState<Result | null>(null);
  const [copied, setCopied] = useState(false);
  const [showCurl, setShowCurl] = useState(false);
  const [subscriptionEndpointId, setSubscriptionEndpointId] = useState("");
  const [subscriptionRetrySeconds, setSubscriptionRetrySeconds] = useState(60);
  const environment = useQuery({ queryKey: ["environment-info"], queryFn: () => fetch("/api/environment", { cache: "no-store" }).then(r => r.json() as Promise<EnvironmentInfo>) });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { eventType: "payment.completed", payload: JSON.stringify(samples["payment.completed"], null, 2), idempotencyKey: "" },
  });
  const values = useWatch({ control: form.control });
  const deliveries = useQuery({
    queryKey: ["playground-deliveries", result?.event.id],
    queryFn: () => api<Result["deliveries"]>(paths.eventDeliveries(result!.event.id)),
    enabled: !!result,
    refetchInterval: query => query.state.data?.some(d => ["PENDING", "PROCESSING", "RETRY_WAIT"].includes(d.status)) ? 3500 : false,
  });

  const select = (type: string) => {
    form.setValue("eventType", type, { shouldValidate: true });
    form.setValue("payload", JSON.stringify(samples[type], null, 2), { shouldValidate: true });
  };
  const format = () => {
    try { form.setValue("payload", JSON.stringify(JSON.parse(form.getValues("payload")), null, 2), { shouldValidate: true }); form.clearErrors("payload"); }
    catch { form.setError("payload", { message: "Invalid JSON syntax. Check quotes, commas, and brackets." }); }
  };
  const submit = form.handleSubmit(async value => {
    let payload: unknown;
    try { payload = JSON.parse(value.payload); }
    catch { form.setError("payload", { message: "Payload must be valid JSON." }); return; }
    try {
      const event = await create.mutateAsync({ eventType: value.eventType, payload, idempotencyKey: value.idempotencyKey || undefined });
      setResult({ event, deliveries: [] });
      toast.success("Event accepted", { description: shortId(event.id) });
    } catch (error) { toast.error("Event was not accepted", { description: error instanceof Error ? error.message : undefined }); }
  });
  const currentDeliveries = deliveries.data ?? result?.deliveries ?? [];
  const serverUrl = environment.data?.serverUrl ?? "http://localhost:8080";
  const selectedEventType = values.eventType ?? "";
  const curl = makeCurl(serverUrl, values.eventType ?? "", values.payload ?? "{}", values.idempotencyKey ?? "", environment.data?.hasApiKey ?? false);
  const copyCurl = async () => {
    try { await navigator.clipboard.writeText(curl); setCopied(true); window.setTimeout(() => setCopied(false), 1600); }
    catch { toast.error("Clipboard access is unavailable"); }
  };

  return <div className="page-enter">
    <div className="page-heading-row"><div><div className="eyebrow"><span className="eyebrow-line"/>DEVELOPER TOOLS</div><h1>Event playground</h1><p className="page-subtitle">Send a real event, then inspect which subscriptions turn it into delivery jobs.</p></div><span className={`connection-pill ${environment.isError ? "offline" : "online"}`}><span className="connection-dot"/>{environment.data?.name ?? "Connected server"}</span></div>

    <section className="playground-guide surface">
      <div className="guide-heading"><span className="guide-icon"><Waypoints size={17}/></span><div><h2>How event delivery works</h2><p>The playground sends a real request to your selected RelayForge server.</p></div><span className="guide-api">POST /api/v1/events</span></div>
      <div className="guide-steps">
        <div className="guide-step"><span>01</span><div><b>Register an endpoint</b><small>Add a receiver under <Link href="/endpoints">Endpoints <ExternalLink size={10}/></Link>. For local development, use a public test receiver such as webhook.site.</small></div></div>
        <div className="guide-step"><span>02</span><div><b>Subscribe to this event type</b><small>Use the quick subscription control below to add this exact type to an active endpoint.</small></div></div>
        <div className="guide-step"><span>03</span><div><b>Send and watch the result</b><small>RelayForge saves the event, creates jobs for matching active subscriptions, then the worker attempts delivery.</small></div></div>
      </div>
      <div className="guide-warning"><AlertTriangle size={14}/><span>Sending here creates a real event. If a matching endpoint and delivery worker are active, RelayForge will send an HTTP request to that receiver.</span></div>
    </section>

    <div className="detail-grid playground-grid">
      <section className="surface detail-card"><div className="section-heading"><div><h2 className="detail-title">Compose request</h2><p>Choose a type and edit the JSON body before sending.</p></div><Sparkles size={16} color="#a79aff"/></div>
        <div className="dev-subscribe"><div className="dev-subscribe-copy"><span className="dev-subscribe-icon"><Radio size={14}/></span><span><b>Route this event</b><small>Subscribe an active endpoint to <code>{selectedEventType || "your event type"}</code></small></span></div>{(endpoints.data??[]).filter(endpoint=>endpoint.active).length===0?<Link className="button button-secondary" href="/endpoints"><Plus size={13}/> Add an active endpoint</Link>:<div className="dev-subscribe-controls"><select className="filter-select" aria-label="Choose active endpoint" value={subscriptionEndpointId} onChange={event=>setSubscriptionEndpointId(event.target.value)}><option value="">Choose endpoint</option>{(endpoints.data??[]).filter(endpoint=>endpoint.active).map(endpoint=><option key={endpoint.id} value={endpoint.id}>{endpoint.description||endpoint.url}</option>)}</select><input aria-label="Retry interval in seconds" className="text-input mono retry-seconds-input" type="number" min={1} max={604800} value={subscriptionRetrySeconds} onChange={event=>setSubscriptionRetrySeconds(Math.max(1,Math.min(604800,Number(event.target.value)||1)))}/><button type="button" className="button button-secondary" disabled={!subscriptionEndpointId||!selectedEventType.trim()||createSubscription.isPending} onClick={()=>createSubscription.mutate({endpointId:subscriptionEndpointId,eventType:selectedEventType.trim(),retryIntervalSeconds:subscriptionRetrySeconds},{onSuccess:()=>toast.success(`Subscribed to ${selectedEventType.trim()}`),onError:error=>toast.error("Could not add subscription",{description:error.message})})}>{createSubscription.isPending?<LoaderCircle size={13} className="spin"/>:<Plus size={13}/>} Subscribe</button></div>}</div>
        <div className="template-row" style={{ marginTop: 15, flexWrap: "wrap" }}><span>Examples</span>{Object.keys(samples).map(type => <button key={type} type="button" className="template-chip" onClick={() => select(type)}>{type}</button>)}</div>
        <form className="form-stack" onSubmit={submit}>
          <label>Event type<input className="text-input mono" maxLength={100} {...form.register("eventType")}/>{form.formState.errors.eventType&&<span className="field-error">{form.formState.errors.eventType.message}</span>}</label>
          <label>JSON payload <button type="button" className="format-button" onClick={format}><Braces size={13}/> Format</button><textarea className="code-editor" spellCheck={false} {...form.register("payload")}/>{form.formState.errors.payload&&<span className="field-error">{form.formState.errors.payload.message}</span>}</label>
          <label>Idempotency key <span className="optional">optional</span><input className="text-input mono" maxLength={255} placeholder="payment-123" {...form.register("idempotencyKey")}/><small className="form-help">Reuse a key to avoid creating the same event twice. Reusing it with a different type or payload returns a conflict.</small></label>
          <button className="button button-primary" disabled={create.isPending}>{create.isPending?<LoaderCircle className="spin" size={14}/>:<Play size={14}/>} {create.isPending?"Sending event…":"Send real event"}</button>
        </form>
        <div className="request-preview"><div className="request-preview-head"><span><Code2 size={13}/> REQUEST PREVIEW</span><button type="button" onClick={()=>setShowCurl(v=>!v)}>{showCurl?"Hide curl":"Show curl"}</button></div><div className="request-line"><b>POST</b><code>{serverUrl}/api/v1/events</code></div>{showCurl&&<div className="curl-box"><pre>{curl}</pre><button type="button" className="copy-curl" onClick={copyCurl}>{copied?<Check size={13}/>:<Clipboard size={13}/>} {copied?"Copied":"Copy curl"}</button></div>}</div>
      </section>

      <section className="surface detail-card"><div className="section-heading"><div><h2 className="detail-title">Execution result</h2><p>Event response and delivery job tracking</p></div>{result&&<StatusBadge status={result.event.status}/>}</div>
        {!result?<div className="empty-inline" style={{ height: 270 }}><Activity size={20}/><span>When you send an event, its server response and matching delivery jobs will appear here.</span></div>:<div className="form-stack" style={{ marginTop: 16 }}>
          <div className="result-success"><Check size={15}/><span><b>Event accepted</b><small>Saved by RelayForge at {formatDate(result.event.createdAt)}</small></span><Link href={`/events/${result.event.id}`}>Inspect <ExternalLink size={11}/></Link></div>
          <div className="detail-list"><dt>Event ID</dt><dd className="mono">{result.event.id}</dd><dt>Event type</dt><dd className="mono">{result.event.eventType}</dd><dt>Delivery jobs</dt><dd>{deliveries.isPending?"Looking up…":currentDeliveries.length}</dd><dt>Active endpoints</dt><dd>{endpoints.data?.filter(endpoint=>endpoint.active).length ?? "—"}</dd></div>
          <div className="route-graph"><div className="route-node"><span>Event</span><b>{result.event.eventType}</b></div><div className="route-link animated"/><div className="route-node"><span>Matching jobs</span><b>{deliveries.isPending?"Checking…":`${currentDeliveries.length} created`}</b></div></div>
          {deliveries.isError&&<ErrorState message={deliveries.error.message}/>}
          {currentDeliveries.length===0&&!deliveries.isPending&&!deliveries.isError&&<div className="unavailable"><ShieldAlert size={15}/><span>The event was saved, but no delivery jobs matched it. Check that an endpoint is active and subscribed to the exact event type <code>{result.event.eventType}</code>.</span></div>}
          {currentDeliveries.map(delivery=>{const endpoint=endpoints.data?.find(item=>item.id===delivery.endpointId);return <Link key={delivery.id} href={`/deliveries/${delivery.id}`} className="recent-event"><span className="event-type-icon"><Radio size={12}/></span><span className="recent-event-main"><b>{endpoint?.description||shortId(delivery.endpointId)}</b><small>{endpoint?.url ?? `Endpoint ${shortId(delivery.endpointId)}`}</small></span><StatusBadge status={delivery.status}/></Link>})}
          <Link href={`/events/${result.event.id}`} className="button button-secondary">Open event details</Link><div><span className="field-label">Accepted payload</span><JsonViewer value={result.event.payload}/></div>
          <div className="unavailable"><Activity size={15}/><span>Delivery jobs update asynchronously. Pending work is refreshed automatically; open a job to inspect attempts and responses.</span></div>
        </div>}
      </section>
    </div>
  </div>;
}
