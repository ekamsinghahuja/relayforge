import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
export function cn(...inputs: ClassValue[]) { return twMerge(clsx(inputs)); }
export function shortId(id: string) { return `${id.slice(0, 8)}…${id.slice(-4)}`; }
export function formatDate(value: string | null) { return value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—"; }
export function relativeTime(value: string) { const delta=Date.now()-new Date(value).getTime();const minutes=Math.floor(delta/60000);return minutes<1?"just now":minutes<60?`${minutes}m ago`:minutes<1440?`${Math.floor(minutes/60)}h ago`:`${Math.floor(minutes/1440)}d ago`; }
