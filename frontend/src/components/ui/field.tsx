import * as React from "react";

export interface FieldProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
}

/** ids of the hint and error texts, for the control's aria-describedby. */
export function describedBy({ id, hint, error }: FieldProps): string | undefined {
  const ids = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean);
  return ids.length > 0 ? ids.join(" ") : undefined;
}

export const controlClassName =
  "min-h-12 w-full rounded-control border-2 border-border-strong bg-background px-4 text-base text-foreground placeholder:text-muted disabled:cursor-not-allowed disabled:opacity-60 aria-invalid:border-danger";

/** Label, optional hint and optional error around a single form control. */
export function Field({ id, label, hint, error, children }: FieldProps & { children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="font-semibold">
        {label}
      </label>
      {hint && (
        <p id={`${id}-hint`} className="text-sm text-muted">
          {hint}
        </p>
      )}
      {children}
      {error && (
        <p id={`${id}-error`} className="text-sm font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
