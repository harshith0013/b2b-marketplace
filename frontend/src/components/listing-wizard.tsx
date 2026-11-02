"use client";

import { useEffect, useRef, useState } from "react";
import { ArrowLeft, ArrowRight, Camera } from "lucide-react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Select, type SelectOption } from "@/components/ui/select";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { cn } from "@/lib/utils";

type WizardLabels = Dictionary["wizard"];
type StepKey = keyof WizardLabels["steps"];

const STEPS: StepKey[] = ["product", "batch", "quantity", "photos", "review"];

const FIELD_NAMES = [
  "productName",
  "brand",
  "category",
  "batchNumber",
  "dateType",
  "date",
  "quantity",
  "unit",
  "pricePerUnit",
  "minimumOrder",
] as const;

type FieldName = (typeof FIELD_NAMES)[number];
type Values = Record<FieldName, string>;

const EMPTY_VALUES = Object.fromEntries(FIELD_NAMES.map((name) => [name, ""])) as Values;

function toOptions(labels: Record<string, string>): SelectOption[] {
  return Object.entries(labels).map(([value, label]) => ({ value, label }));
}

interface ListingWizardProps {
  labels: WizardLabels;
  common: Pick<Dictionary["common"], "back" | "next" | "stepOf">;
}

/**
 * Step-by-step listing form. A shell only: nothing is validated or saved until
 * listings are stored in Phase 5.
 */
export function ListingWizard({ labels, common }: ListingWizardProps) {
  const [stepIndex, setStepIndex] = useState(0);
  const [values, setValues] = useState<Values>(EMPTY_VALUES);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const hasNavigated = useRef(false);

  // Move focus to the new step's heading so screen reader users hear where they are.
  useEffect(() => {
    if (hasNavigated.current) headingRef.current?.focus();
  }, [stepIndex]);

  const goTo = (index: number) => {
    hasNavigated.current = true;
    setStepIndex(index);
  };

  const step = STEPS[stepIndex];
  const isFirst = stepIndex === 0;
  const isLast = stepIndex === STEPS.length - 1;
  const { fields } = labels;
  const options = {
    category: toOptions(labels.categories),
    dateType: toOptions(labels.dateTypes),
    unit: toOptions(labels.units),
  };

  const bind = (name: FieldName) => ({
    id: `listing-${name}`,
    name,
    value: values[name],
    onChange: (event: { target: { value: string } }) =>
      setValues((current) => ({ ...current, [name]: event.target.value })),
  });

  return (
    <form className="flex flex-col gap-6" onSubmit={(event) => event.preventDefault()}>
      <div className="flex flex-col gap-3">
        <p className="font-semibold text-muted">
          {format(common.stepOf, { current: stepIndex + 1, total: STEPS.length })}
        </p>
        <ol aria-label={labels.progressLabel} className="hidden gap-2 sm:flex">
          {STEPS.map((key, index) => (
            <li
              key={key}
              aria-current={index === stepIndex ? "step" : undefined}
              className={cn(
                "flex-1 rounded-full border-2 px-3 py-1 text-center text-sm font-semibold",
                index === stepIndex && "border-accent bg-accent text-accent-foreground",
                index < stepIndex && "border-foreground",
                index > stepIndex && "border-border text-muted",
              )}
            >
              {labels.steps[key]}
            </li>
          ))}
        </ol>
      </div>

      <h2 ref={headingRef} tabIndex={-1} className="text-heading font-bold">
        {labels.steps[step]}
      </h2>

      <div className="flex flex-col gap-5">
        {step === "product" && (
          <>
            <Input label={fields.productName} hint={fields.productNameHint} {...bind("productName")} />
            <Input label={fields.brand} {...bind("brand")} />
            <Select
              label={fields.category}
              placeholder={fields.categoryPlaceholder}
              options={options.category}
              {...bind("category")}
            />
          </>
        )}

        {step === "batch" && (
          <>
            <Input label={fields.batchNumber} {...bind("batchNumber")} />
            <Select
              label={fields.dateType}
              placeholder={fields.dateTypePlaceholder}
              options={options.dateType}
              {...bind("dateType")}
            />
            <Input type="date" label={fields.date} hint={fields.dateHint} {...bind("date")} />
          </>
        )}

        {step === "quantity" && (
          <>
            <Input
              type="number"
              inputMode="numeric"
              min={1}
              label={fields.quantity}
              {...bind("quantity")}
            />
            <Select
              label={fields.unit}
              placeholder={fields.unitPlaceholder}
              options={options.unit}
              {...bind("unit")}
            />
            <Input
              type="number"
              inputMode="decimal"
              min={0}
              step="0.01"
              label={fields.pricePerUnit}
              hint={fields.pricePerUnitHint}
              {...bind("pricePerUnit")}
            />
            <Input
              type="number"
              inputMode="numeric"
              min={1}
              label={fields.minimumOrder}
              {...bind("minimumOrder")}
            />
          </>
        )}

        {step === "photos" && (
          <EmptyState
            icon={<Camera />}
            title={labels.photosTitle}
            description={labels.photosDescription}
          />
        )}

        {step === "review" && (
          <ReviewSummary labels={labels} values={values} options={options} />
        )}
      </div>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
        {!isFirst ? (
          <Button type="button" variant="outline" onClick={() => goTo(stepIndex - 1)}>
            <ArrowLeft aria-hidden="true" className="size-5" />
            {common.back}
          </Button>
        ) : (
          <span />
        )}
        {!isLast && (
          <Button type="button" onClick={() => goTo(stepIndex + 1)}>
            {common.next}
            <ArrowRight aria-hidden="true" className="size-5" />
          </Button>
        )}
        {isLast && (
          <div className="flex flex-col gap-2 sm:items-end">
            <Button type="submit" variant="accent" disabled aria-describedby="listing-submit-note">
              {labels.submit}
            </Button>
            <p id="listing-submit-note" className="text-sm text-muted">
              {labels.submitNote}
            </p>
          </div>
        )}
      </div>
    </form>
  );
}

interface ReviewSummaryProps {
  labels: WizardLabels;
  values: Values;
  options: Record<"category" | "dateType" | "unit", SelectOption[]>;
}

function ReviewSummary({ labels, values, options }: ReviewSummaryProps) {
  const optionLabel = (name: keyof ReviewSummaryProps["options"]) =>
    options[name].find((option) => option.value === values[name])?.label ?? "";

  const rows: [string, string][] = [
    [labels.fields.productName, values.productName],
    [labels.fields.brand, values.brand],
    [labels.fields.category, optionLabel("category")],
    [labels.fields.batchNumber, values.batchNumber],
    [labels.fields.dateType, optionLabel("dateType")],
    [labels.fields.date, values.date],
    [labels.fields.quantity, [values.quantity, optionLabel("unit")].filter(Boolean).join(" ")],
    [labels.fields.pricePerUnit, values.pricePerUnit],
    [labels.fields.minimumOrder, values.minimumOrder],
  ];

  return (
    <div className="flex flex-col gap-3">
      <p className="text-muted">{labels.reviewHint}</p>
      <dl className="divide-y-2 divide-border rounded-card border-2 border-border">
        {rows.map(([label, value]) => (
          <div key={label} className="flex flex-col gap-0.5 px-4 py-3 sm:flex-row sm:justify-between">
            <dt className="text-muted">{label}</dt>
            <dd className={cn("font-semibold", !value && "text-muted")}>
              {value || labels.notProvided}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
