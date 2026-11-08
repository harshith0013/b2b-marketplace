"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import type { Dictionary } from "@/i18n/dictionaries";

interface InviteFormProps {
  labels: Dictionary["business"];
  roles: Dictionary["account"]["roles"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}

export function InviteForm({ labels, roles, action }: InviteFormProps) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );

  return (
    <form action={formAction} className="flex flex-col gap-5">
      <Input
        id="invite-email"
        name="email"
        type="email"
        label={labels.inviteEmail}
        hint={labels.inviteHint}
        required
        maxLength={320}
        autoComplete="off"
      />
      <Select
        id="invite-role"
        name="role"
        label={labels.inviteRole}
        options={[
          { value: "STAFF", label: roles.STAFF },
          { value: "MANAGER", label: roles.MANAGER },
        ]}
        defaultValue="STAFF"
      />
      <FormMessage
        state={state}
        errors={labels.errors}
        success={labels.invited}
      />
      <Button type="submit" disabled={pending} className="self-start">
        {labels.invite}
      </Button>
    </form>
  );
}
