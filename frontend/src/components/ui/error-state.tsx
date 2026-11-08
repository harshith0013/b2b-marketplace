import * as React from "react";
import { CircleAlert } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "./button";

export interface ErrorStateProps {
  title: string;
  description?: string;
  /** Shows a retry button when both are given. */
  onRetry?: () => void;
  retryLabel?: string;
  className?: string;
}

export function ErrorState({
  title,
  description,
  onRetry,
  retryLabel,
  className,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center gap-3 rounded-card bg-danger-soft px-6 py-10 text-center",
        className,
      )}
    >
      <CircleAlert aria-hidden="true" className="size-10 text-danger" />
      <p className="text-title font-bold text-danger">{title}</p>
      {description && <p className="max-w-prose">{description}</p>}
      {onRetry && retryLabel && (
        <Button variant="outline" onClick={onRetry}>
          {retryLabel}
        </Button>
      )}
    </div>
  );
}
