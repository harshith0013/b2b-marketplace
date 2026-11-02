"use client";

import * as React from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

export const TOAST_DURATION_MS = 6000;

type ToastVariant = "info" | "success" | "danger";

interface ToastMessage {
  id: number;
  message: string;
  variant: ToastVariant;
}

type ShowToast = (message: string, variant?: ToastVariant) => void;

const ToastContext = React.createContext<ShowToast | null>(null);

export function useToast(): ShowToast {
  const show = React.useContext(ToastContext);
  if (!show) throw new Error("useToast must be used inside <ToastProvider>");
  return show;
}

const variantClassName: Record<ToastVariant, string> = {
  info: "border-info bg-info-soft text-info",
  success: "border-success bg-success-soft text-success",
  danger: "border-danger bg-danger-soft text-danger",
};

/**
 * Short, non-blocking messages. The live region is always in the page so
 * screen readers announce each message as it appears.
 */
export function ToastProvider({
  dismissLabel,
  children,
}: {
  dismissLabel: string;
  children: React.ReactNode;
}) {
  const [toasts, setToasts] = React.useState<ToastMessage[]>([]);
  const nextId = React.useRef(0);

  const dismiss = React.useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = React.useCallback<ShowToast>(
    (message, variant = "info") => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, message, variant }]);
      setTimeout(() => dismiss(id), TOAST_DURATION_MS);
    },
    [dismiss],
  );

  return (
    <ToastContext value={show}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-4 bottom-4 z-50 mx-auto flex max-w-md flex-col gap-2"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={cn(
              "pointer-events-auto flex items-center justify-between gap-3 rounded-control border-2 py-1 pr-1 pl-4 font-semibold shadow-card",
              variantClassName[toast.variant],
            )}
          >
            <p className="py-2">{toast.message}</p>
            <button
              type="button"
              aria-label={dismissLabel}
              onClick={() => dismiss(toast.id)}
              className="inline-flex size-12 shrink-0 items-center justify-center rounded-control"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext>
  );
}
