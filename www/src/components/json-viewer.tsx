"use client";
import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { toast } from "sonner";
export function JsonViewer({value}:{value:unknown}){const [copied,setCopied]=useState(false);const text=typeof value==="string"?(()=>{try{return JSON.stringify(JSON.parse(value),null,2)}catch{return value}})():JSON.stringify(value,null,2);return <div style={{position:"relative"}}><button className="icon-button" style={{position:"absolute",right:8,top:8,zIndex:1}} aria-label="Copy JSON" onClick={async()=>{await navigator.clipboard.writeText(text);setCopied(true);toast.success("JSON copied");setTimeout(()=>setCopied(false),1200)}}>{copied?<Check size={14}/>:<Copy size={14}/>}</button><pre className="json-block">{text}</pre></div>}
