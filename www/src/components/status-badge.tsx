import { cn } from "@/lib/utils";
const tones: Record<string,string> = { DONE:"status-delivered",DELIVERED:"status-delivered",SUCCESS:"status-delivered",PENDING:"status-pending",PROCESSING:"status-processing",RETRY_WAIT:"status-retry",FAILED:"status-failed",CANCELLED:"status-muted" };
export function StatusBadge({status}:{status:string}) { return <span className={cn("status-pill",tones[status]??"status-muted")}><i className="status-dot" />{status.replaceAll("_"," ")}</span>; }
