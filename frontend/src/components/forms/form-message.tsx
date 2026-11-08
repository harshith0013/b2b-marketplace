import type { FormState } from "@/lib/actions/form-state";

interface FormMessageProps {
  state: FormState;
  errors: Record<NonNullable<FormState["error"]>, string>;
  success?: string;
}

/** The result of the last submit: an error is announced immediately, success politely. */
export function FormMessage({ state, errors, success }: FormMessageProps) {
  if (state.error) {
    return (
      <p
        role="alert"
        className="rounded-control bg-danger-soft px-4 py-3 font-semibold text-danger"
      >
        {errors[state.error]}
      </p>
    );
  }
  if (state.ok && success) {
    return (
      <p
        role="status"
        className="rounded-control bg-success-soft px-4 py-3 font-semibold text-success"
      >
        {success}
      </p>
    );
  }
  return null;
}
