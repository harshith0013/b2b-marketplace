"use client";

import { useState } from "react";
import { Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import type { Dictionary } from "@/i18n/dictionaries";

interface AssistantDialogProps {
  labels: Dictionary["home"]["assistant"];
  comingSoonLabel: string;
  closeLabel: string;
}

// Entry point for the AI assistant (Phase 7). For now it only explains what is coming.
export function AssistantDialog({ labels, comingSoonLabel, closeLabel }: AssistantDialogProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button variant="outline" size="lg" className="w-full" onClick={() => setOpen(true)}>
        <Sparkles aria-hidden="true" className="size-5" />
        {labels.open}
      </Button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title={labels.title}
        closeLabel={closeLabel}
      >
        <div className="flex flex-col gap-3">
          <Badge variant="info" className="self-start">
            {comingSoonLabel}
          </Badge>
          <p>{labels.body}</p>
          <p className="text-muted">{labels.note}</p>
        </div>
      </Dialog>
    </>
  );
}
