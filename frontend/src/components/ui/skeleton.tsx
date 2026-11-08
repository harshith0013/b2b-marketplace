import * as React from "react";
import { cn } from "@/lib/utils";

/** A grey placeholder shape while content loads. Hidden from screen readers. */
export function Skeleton({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn("animate-pulse rounded-control bg-border", className)}
      {...props}
    />
  );
}
