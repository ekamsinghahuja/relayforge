"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, Eye, EyeOff, Globe2, KeyRound, LoaderCircle, Radio, ShieldCheck, Sparkles } from "lucide-react";
import { AppShell } from "@/components/app-shell";

type Environment = { configured: true; name: string; serverUrl: string };
type Props = { onConnected: (environment: Environment) => void };

export function EnvironmentSetup({ onConnected }: Props) {
  const [name, setName] = useState("My RelayForge");
  const [serverUrl, setServerUrl] = useState("http://localhost:8080");
  const [apiKey, setApiKey] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError("");
    try {
      const response = await fetch("/api/environment", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, serverUrl, apiKey }),
      });
      const result = await response.json() as Environment | { message?: string };
      if (!response.ok || !("configured" in result)) throw new Error("message" in result && result.message ? result.message : "Could not connect to that server.");
      onConnected(result);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not connect to that server.");
    } finally { setBusy(false); }
  }

  return <main className="environment-screen">
    <div className="environment-orbit orbit-one"/><div className="environment-orbit orbit-two"/>
    <div className="environment-content">
      <Link className="brand-lockup environment-brand" href="/"><span className="brand-mark"><svg viewBox="0 0 36 36" fill="none"><path d="M6 11.5 18 5l12 6.5v13L18 31 6 24.5v-13Z" stroke="currentColor" strokeWidth="1.8"/><path d="m11 18 4.3 4.2L25.5 12" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/></svg></span><span className="brand-name">relay<span>forge</span></span></Link>
      <section className="environment-card">
        <div className="environment-card-top"><span className="environment-icon"><Radio size={20}/></span><span className="environment-live"><i/> ENVIRONMENT SETUP</span></div>
        <div className="environment-intro"><div className="eyebrow"><Sparkles size={13}/> YOUR WORKSPACE, YOUR SERVER</div><h1>Connect your<br/><em>RelayForge</em> server.</h1><p>Choose the API your console should use. Your workspace stays available in this browser session, so you can move straight into the console.</p></div>
        <form className="environment-form" onSubmit={submit}>
          <label>Workspace name<span className="environment-input"><Radio size={15}/><input required maxLength={48} value={name} onChange={e=>setName(e.target.value)} placeholder="Production"/></span></label>
          <label>Server URL<span className="environment-input"><Globe2 size={15}/><input required type="url" value={serverUrl} onChange={e=>setServerUrl(e.target.value)} placeholder="https://api.example.com"/></span><small>For local development, use <code>http://localhost:8080</code></small></label>
          <label>API key <span className="field-optional">OPTIONAL</span><span className="environment-input"><KeyRound size={15}/><input type={showKey?"text":"password"} autoComplete="off" value={apiKey} onChange={e=>setApiKey(e.target.value)} placeholder="Enter the key for this server"/><button type="button" className="reveal-key" aria-label={showKey?"Hide API key":"Show API key"} onClick={()=>setShowKey(v=>!v)}>{showKey?<EyeOff size={15}/>:<Eye size={15}/>}</button></span><small>The key stays in this console server’s memory and is sent to your API by the server proxy.</small></label>
          {error&&<div role="alert" className="environment-error">{error}</div>}
          <button type="submit" className="environment-submit" disabled={busy}>{busy?<><LoaderCircle size={16} className="spin"/> Checking server…</>:<>Connect workspace <ArrowRight size={16}/></>}</button>
        </form>
        <div className="environment-assurance"><ShieldCheck size={15}/><span>We’ll verify the server is reachable before opening your console.</span></div>
      </section>
      <div className="environment-foot"><span><i/> SESSION CONFIGURATION</span><span>RELAYFORGE CONSOLE <b>·</b> SERVER SIDE PROXY</span></div>
    </div>
  </main>;
}

export function EnvironmentGate({ children }: { children: React.ReactNode }) {
  const [environment, setEnvironment] = useState<Environment | null>(null);
  const [checked, setChecked] = useState(false);
  useEffect(() => {
    let active = true;
    fetch("/api/environment", { cache: "no-store" }).then(r=>r.json()).then((data: { configured?: boolean; name?: string; serverUrl?: string })=>{
      if(active&&data.configured&&data.name&&data.serverUrl)setEnvironment({configured:true,name:data.name,serverUrl:data.serverUrl});
    }).catch(()=>{}).finally(()=>{if(active)setChecked(true)});
    return ()=>{active=false};
  }, []);
  if (!checked) return <div className="environment-loading"><LoaderCircle size={18} className="spin"/> Loading your workspace…</div>;
  if (!environment) return <EnvironmentSetup onConnected={setEnvironment}/>;
  return <AppShell environment={environment} onSwitchEnvironment={async () => {
    await fetch("/api/environment", { method: "DELETE" });
    setEnvironment(null);
  }}>{children}</AppShell>;
}
