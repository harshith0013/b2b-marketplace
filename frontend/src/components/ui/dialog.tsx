"use client";

import * as React from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "./button";

export interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  closeLabel: string;
  children: React.ReactNode;
  className?: string;
}

/**
 * A modal built on the native <dialog> element, which gives focus trapping,
 * Escape to close and an inert background without extra JavaScript.
 */
export function Dialog({ open, onClose, title, closeLabel, children, className }: DialogProps) {
  const ref = React.useRef<HTMLDialogElement>(null);
  const titleId = React.useId();

  React.useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      className={cn(
        "m-auto w-[min(32rem,calc(100%-2rem))] rounded-card border-2 border-border bg-background p-0 text-foreground shadow-card",
        className,
      )}
    >
      <div className="flex items-start justify-between gap-4 border-b-2 border-border p-5">
        <h2 id={titleId} className="text-title font-bold">
          {title}
        </h2>
        <Button variant="ghost" className="-m-2 px-3" aria-label={closeLabel} onClick={onClose}>
          <X aria-hidden="true" className="size-5" />
        </Button>
      </div>
      <div className="p-5">{children}</div>
    </dialog>
  );
}
