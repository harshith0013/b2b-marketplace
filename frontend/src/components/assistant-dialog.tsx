"use client";

import { useState } from "react";
import { useFormStatus } from "react-dom";
import { Search, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, controlClassName } from "@/components/ui/field";
import type { Dictionary } from "@/i18n/dictionaries";

interface AssistantDialogProps {
  labels: Dictionary["home"]["assistant"];
  closeLabel: string;
  /** Server Action: sends the question and opens the offers page. */
  action: (form: FormData) => Promise<void>;
}

function SubmitButton({ label }: { label: string }) {
  const { pending } = useFormStatus();
  return (
    <Button
      type="submit"
      variant="accent"
      disabled={pending}
      className="self-start"
    >
      <Search aria-hidden="true" className="size-5" />
      {label}
    </Button>
  );
}

/** The search assistant: a question in the shopper's own words, in any of the three languages. */
export function AssistantDialog({
  labels,
  closeLabel,
  action,
}: AssistantDialogProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button
        variant="outline"
        size="lg"
        className="w-full"
        onClick={() => setOpen(true)}
      >
        <Sparkles aria-hidden="true" className="size-5" />
        {labels.open}
      </Button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title={labels.title}
        closeLabel={closeLabel}
      >
        <form action={action} className="flex flex-col gap-4">
          <p>{labels.body}</p>
          <Field
            id="assistant-question"
            label={labels.label}
            hint={labels.privacy}
          >
            <textarea
              id="assistant-question"
              name="question"
              required
              maxLength={300}
              rows={3}
              aria-describedby="assistant-question-hint"
              className={`${controlClassName} py-3`}
            />
          </Field>
          <SubmitButton label={labels.submit} />
          <p className="text-sm text-muted">{labels.note}</p>
        </form>
      </Dialog>
    </>
  );
}
