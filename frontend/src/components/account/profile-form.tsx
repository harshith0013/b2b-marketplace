"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { localeLabels, locales } from "@/i18n/config";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import type { UserProfile } from "@/lib/api/types";
import type { Dictionary } from "@/i18n/dictionaries";

interface ProfileFormProps {
  labels: Dictionary["account"];
  errors: Dictionary["business"]["errors"];
  profile: UserProfile;
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}

export function ProfileForm({
  labels,
  errors,
  profile,
  action,
}: ProfileFormProps) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );

  return (
    <form action={formAction} className="flex flex-col gap-5">
      <p>
        <span className="text-muted">{labels.email}: </span>
        <span className="font-semibold break-all">{profile.email}</span>
      </p>
      <Input
        id="profile-displayName"
        name="displayName"
        label={labels.displayName}
        defaultValue={profile.displayName ?? ""}
        maxLength={100}
        autoComplete="name"
      />
      <Select
        id="profile-preferredLocale"
        name="preferredLocale"
        label={labels.language}
        options={locales.map((locale) => ({
          value: locale,
          label: localeLabels[locale],
        }))}
        defaultValue={profile.preferredLocale}
      />
      <FormMessage state={state} errors={errors} success={labels.saved} />
      <Button
        type="submit"
        variant="outline"
        disabled={pending}
        className="self-start"
      >
        {labels.save}
      </Button>
    </form>
  );
}
