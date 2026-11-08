"use client";

import { useActionState } from "react";
import { Upload } from "lucide-react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Field, controlClassName } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import { PHOTO_KINDS, type PhotoKind } from "@/lib/api/types";

interface PhotoUploadFormProps {
  labels: Dictionary["listings"];
  kinds: Dictionary["labels"]["photoKinds"];
  errors: Dictionary["business"]["errors"];
  /** The kind still missing, chosen first so the seller is guided to it. */
  suggestedKind: PhotoKind;
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}

export function PhotoUploadForm({
  labels,
  kinds,
  errors,
  suggestedKind,
  action,
}: PhotoUploadFormProps) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );

  return (
    <form action={formAction} className="flex flex-col gap-4">
      <Select
        id="photo-kind"
        name="kind"
        label={labels.photoKind}
        options={PHOTO_KINDS.map((value) => ({ value, label: kinds[value] }))}
        defaultValue={suggestedKind}
        key={suggestedKind}
      />
      <Field
        id="photo-file"
        label={labels.photoFile}
        hint={labels.photoFileHint}
      >
        <input
          id="photo-file"
          name="file"
          type="file"
          accept="image/jpeg,image/png"
          capture="environment"
          required
          aria-describedby="photo-file-hint"
          className={`${controlClassName} py-2`}
        />
      </Field>
      <FormMessage state={state} errors={errors} success={labels.uploaded} />
      <Button type="submit" disabled={pending} className="self-start">
        <Upload aria-hidden="true" className="size-5" />
        {labels.upload}
      </Button>
    </form>
  );
}
