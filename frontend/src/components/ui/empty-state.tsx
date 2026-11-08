import * as React from "react";
import { cn } from "@/lib/utils";

export interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-3 rounded-card border-2 border-dashed border-border px-6 py-10 text-center",
        className,
      )}
    >
      {icon && (
        <span aria-hidden="true" className="text-muted [&_svg]:size-10">
          {icon}
        </span>
      )}
      <p className="text-title font-bold">{title}</p>
      {description && <p className="max-w-prose text-muted">{description}</p>}
      {action}
    </div>
  );
}
