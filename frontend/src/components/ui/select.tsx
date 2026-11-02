import * as React from "react";
import { cn } from "@/lib/utils";
import { Field, controlClassName, describedBy, type FieldProps } from "./field";

export interface SelectOption {
  value: string;
  label: string;
}

export type SelectProps = FieldProps &
  Omit<React.SelectHTMLAttributes<HTMLSelectElement>, "id"> & {
    options: SelectOption[];
    /** Shown as an empty first choice so nothing is pre-selected by accident. */
    placeholder?: string;
  };

// A native <select>: the phone's own picker is the easiest to use on small screens.
export function Select({ id, label, hint, error, options, placeholder, className, ...props }: SelectProps) {
  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <select
        id={id}
        aria-describedby={describedBy({ id, label, hint, error })}
        aria-invalid={error ? true : undefined}
        className={cn(controlClassName, className)}
        {...props}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </Field>
  );
}
