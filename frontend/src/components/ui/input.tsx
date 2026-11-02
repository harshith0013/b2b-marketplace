import * as React from "react";
import { cn } from "@/lib/utils";
import { Field, controlClassName, describedBy, type FieldProps } from "./field";

export type InputProps = FieldProps & Omit<React.InputHTMLAttributes<HTMLInputElement>, "id">;

export function Input({ id, label, hint, error, className, ...props }: InputProps) {
  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <input
        id={id}
        aria-describedby={describedBy({ id, label, hint, error })}
        aria-invalid={error ? true : undefined}
        className={cn(controlClassName, className)}
        {...props}
      />
    </Field>
  );
}
