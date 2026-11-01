"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { Toaster } from "sonner";
import { MotionConfig } from "motion/react";
export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: false } } }));
  return <MotionConfig reducedMotion="user"><QueryClientProvider client={client}>{children}<Toaster theme="dark" position="bottom-right" richColors /></QueryClientProvider></MotionConfig>;
}
