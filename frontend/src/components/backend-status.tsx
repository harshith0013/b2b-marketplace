"use client";

import { useEffect, useState } from "react";
import type { HealthSummary, ServiceState } from "@/lib/backend-health";
import { cn } from "@/lib/utils";

export interface StatusLabels {
  heading: string;
  website: string;
  server: string;
  checking: string;
  working: string;
  unavailable: string;
}

type Displayed = "checking" | "working" | "unavailable";

function toDisplayed(state: ServiceState | undefined): Displayed {
  if (state === undefined) return "checking";
  return state === "UP" ? "working" : "unavailable";
}

function StatusRow({
  name,
  state,
  labels,
}: {
  name: string;
  state: Displayed;
  labels: StatusLabels;
}) {
  return (
    <div className="flex items-center justify-between gap-4 py-3">
      <dt>{name}</dt>
      <dd className="flex items-center gap-2 font-semibold">
        <span
          aria-hidden="true"
          className={cn(
            "inline-block size-3 rounded-full",
            state === "working" && "bg-success",
            state === "unavailable" && "bg-danger",
            state === "checking" && "bg-muted",
          )}
        />
        {labels[state]}
      </dd>
    </div>
  );
}

export function BackendStatus({ labels }: { labels: StatusLabels }) {
  const [summary, setSummary] = useState<HealthSummary | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/health", { signal: controller.signal, cache: "no-store" })
      .then((r) => r.json() as Promise<HealthSummary>)
      .then(setSummary)
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      });
    return () => controller.abort();
  }, []);

  const website: Displayed = failed
    ? "unavailable"
    : summary
      ? "working"
      : "checking";
  const server: Displayed = failed
    ? "unavailable"
    : toDisplayed(summary?.backend);

  return (
    <section
      aria-labelledby="status-heading"
      className="rounded-xl border-2 border-border bg-surface p-5"
    >
      <h2 id="status-heading" className="text-lg font-bold">
        {labels.heading}
      </h2>
      <dl aria-live="polite" className="mt-2 divide-y divide-border">
        <StatusRow name={labels.website} state={website} labels={labels} />
        <StatusRow name={labels.server} state={server} labels={labels} />
      </dl>
    </section>
  );
}
