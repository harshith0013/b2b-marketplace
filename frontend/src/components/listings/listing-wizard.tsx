"use client";

import {
  startTransition,
  useActionState,
  useEffect,
  useRef,
  useState,
} from "react";
import Link from "next/link";
import { ArrowLeft, ArrowRight, Plus, Trash2 } from "lucide-react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, type SelectOption } from "@/components/ui/select";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { DraftState } from "@/lib/actions/listings";
import {
  EMPTY_DRAFT,
  MAX_TIERS,
  validateStep,
  type DraftErrors,
  type DraftStep,
  type DraftValues,
} from "@/lib/actions/listing-draft";
import {
  CLEARANCE_REASONS,
  DATE_TYPES,
  GST_RATES_BP,
  PACK_UNITS,
  STOCK_UNITS,
} from "@/lib/api/types";
import { formatGstRate } from "@/lib/money";
import { cn } from "@/lib/utils";

type StepKey = DraftStep | "save";
const STEPS: StepKey[] = ["product", "batch", "price", "save"];

export interface ListingWizardProps {
  labels: Dictionary["wizard"];
  enums: Dictionary["labels"];
  errors: Dictionary["business"]["errors"];
  common: Pick<Dictionary["common"], "back" | "next" | "stepOf">;
  /** Leaf categories, already labelled in the reader's language. */
  categories: SelectOption[];
  /** The business's own products' packs, labelled "Brand Name, 100 g". */
  variants: SelectOption[];
  /** Active warehouses. */
  warehouses: SelectOption[];
  addWarehouseHref: string;
  action: (previous: DraftState, values: DraftValues) => Promise<DraftState>;
}

/**
 * Step-by-step listing form. Values live in this component's state (not the form's), so nothing
 * typed is lost when going back or when saving fails. Each step is checked before moving on; the
 * server checks everything again.
 */
export function ListingWizard(props: ListingWizardProps) {
  const { labels, enums, common, action } = props;
  const [values, setValues] = useState<DraftValues>(() => ({
    ...EMPTY_DRAFT,
    productMode: props.variants.length > 0 ? "existing" : "new",
  }));
  const [fieldErrors, setFieldErrors] = useState<DraftErrors>({});
  const [stepIndex, setStepIndex] = useState(0);
  const [state, save, pending] = useActionState(action, {});
  const [handledState, setHandledState] = useState(state);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const mounted = useRef(false);

  // The server says which step its error belongs to: go back there (adjusting state while
  // rendering, as React recommends, rather than in an effect).
  if (state !== handledState) {
    setHandledState(state);
    if (state.error && state.step) setStepIndex(STEPS.indexOf(state.step));
  }

  // Move focus to the new step's heading so screen reader users hear where they are.
  useEffect(() => {
    if (mounted.current) headingRef.current?.focus();
    mounted.current = true;
  }, [stepIndex]);

  const step = STEPS[stepIndex];
  const set = (patch: Partial<DraftValues>) =>
    setValues((current) => ({ ...current, ...patch }));
  const errorText = (key: string) => {
    const problem = fieldErrors[key];
    return problem ? labels[problem] : undefined;
  };
  const text = (key: keyof DraftValues & string) => ({
    id: `listing-${key}`,
    name: key,
    value: values[key] as string,
    error: errorText(key),
    onChange: (event: { target: { value: string } }) =>
      set({ [key]: event.target.value }),
  });

  const goTo = (index: number) => setStepIndex(index);
  const next = () => {
    if (step !== "save") {
      const problems = validateStep(step, values);
      setFieldErrors(problems);
      if (Object.keys(problems).length > 0) return;
    }
    goTo(stepIndex + 1);
  };
  const submit = () => startTransition(() => save(values));

  const setTier = (
    index: number,
    patch: Partial<DraftValues["tiers"][number]>,
  ) =>
    set({
      tiers: values.tiers.map((tier, i) =>
        i === index ? { ...tier, ...patch } : tier,
      ),
    });

  return (
    <form
      aria-label={labels.title}
      className="flex flex-col gap-6"
      onSubmit={(event) => event.preventDefault()}
      noValidate
    >
      <div className="flex flex-col gap-3">
        <p className="font-semibold text-muted">
          {format(common.stepOf, {
            current: stepIndex + 1,
            total: STEPS.length,
          })}
        </p>
        <ol aria-label={labels.progressLabel} className="hidden gap-2 sm:flex">
          {STEPS.map((key, index) => (
            <li
              key={key}
              aria-current={index === stepIndex ? "step" : undefined}
              className={cn(
                "flex-1 rounded-full border-2 px-3 py-1 text-center text-sm font-semibold",
                index === stepIndex &&
                  "border-accent bg-accent text-accent-foreground",
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
          <ProductStep
            {...props}
            values={values}
            set={set}
            text={text}
            errorText={errorText}
          />
        )}

        {step === "batch" && (
          <>
            {props.warehouses.length === 0 ? (
              <p className="rounded-control bg-warning-soft px-4 py-3 font-semibold text-warning">
                {labels.batch.noWarehouses}{" "}
                <Link
                  href={props.addWarehouseHref}
                  className="underline underline-offset-4"
                >
                  {labels.batch.addWarehouse}
                </Link>
              </p>
            ) : (
              <Select
                label={labels.batch.warehouse}
                placeholder={labels.batch.warehousePlaceholder}
                options={props.warehouses}
                {...text("warehouseId")}
              />
            )}
            <Input
              label={labels.batch.batchNumber}
              hint={labels.batch.batchNumberHint}
              maxLength={50}
              autoComplete="off"
              {...text("batchNumber")}
            />
            <Select
              label={labels.batch.dateType}
              options={DATE_TYPES.map((value) => ({
                value,
                label: enums.dateTypes[value],
              }))}
              {...text("dateType")}
            />
            <Input
              type="date"
              label={labels.batch.expiresOn}
              hint={labels.batch.expiresOnHint}
              {...text("expiresOn")}
            />
            <Input
              type="date"
              label={labels.batch.manufacturedOn}
              {...text("manufacturedOn")}
            />
            <Input
              inputMode="decimal"
              label={labels.batch.mrp}
              hint={labels.batch.mrpHint}
              autoComplete="off"
              {...text("mrp")}
            />
            <Input
              inputMode="numeric"
              label={labels.batch.quantity}
              autoComplete="off"
              {...text("quantity")}
            />
            <Select
              label={labels.batch.quantityUnit}
              options={STOCK_UNITS.map((value) => ({
                value,
                label: enums.stockUnits[value],
              }))}
              {...text("quantityUnit")}
            />
            {values.quantityUnit === "CASE" && (
              <Input
                inputMode="numeric"
                label={labels.batch.packsPerCase}
                autoComplete="off"
                {...text("packsPerCase")}
              />
            )}
          </>
        )}

        {step === "price" && (
          <>
            <Input
              inputMode="numeric"
              label={labels.price.moq}
              hint={labels.price.moqHint}
              autoComplete="off"
              {...text("moq")}
            />
            <fieldset className="flex flex-col gap-4 rounded-card border-2 border-border p-4">
              <legend className="px-1 font-semibold">
                {labels.price.tiersLegend}
              </legend>
              <p className="text-sm text-muted">{labels.price.tiersHint}</p>
              {values.tiers.map((tier, index) => (
                <div
                  key={index}
                  className="flex flex-col gap-3 sm:flex-row sm:items-end"
                >
                  {index === 0 ? (
                    <p className="text-sm text-muted sm:flex-1">
                      {labels.price.firstTierNote}
                    </p>
                  ) : (
                    <div className="sm:flex-1">
                      <Input
                        id={`listing-tier${index}Quantity`}
                        inputMode="numeric"
                        label={labels.price.tierQuantity}
                        value={tier.quantity}
                        error={errorText(`tier${index}Quantity`)}
                        onChange={(event) =>
                          setTier(index, { quantity: event.target.value })
                        }
                      />
                    </div>
                  )}
                  <div className="sm:flex-1">
                    <Input
                      id={`listing-tier${index}Price`}
                      inputMode="decimal"
                      label={labels.price.tierPrice}
                      value={tier.price}
                      error={errorText(`tier${index}Price`)}
                      onChange={(event) =>
                        setTier(index, { price: event.target.value })
                      }
                    />
                  </div>
                  {index > 0 && (
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() =>
                        set({
                          tiers: values.tiers.filter((_, i) => i !== index),
                        })
                      }
                    >
                      <Trash2 aria-hidden="true" className="size-5" />
                      {labels.price.removeTier}
                    </Button>
                  )}
                </div>
              ))}
              {values.tiers.length < MAX_TIERS && (
                <Button
                  type="button"
                  variant="outline"
                  className="self-start"
                  onClick={() =>
                    set({
                      tiers: [...values.tiers, { quantity: "", price: "" }],
                    })
                  }
                >
                  <Plus aria-hidden="true" className="size-5" />
                  {labels.price.addTier}
                </Button>
              )}
            </fieldset>
            <Select
              label={labels.price.gstRate}
              options={GST_RATES_BP.map((bp) => ({
                value: String(bp),
                label: formatGstRate(bp),
              }))}
              {...text("gstRateBp")}
            />
            <Select
              label={labels.price.clearanceReason}
              options={CLEARANCE_REASONS.map((value) => ({
                value,
                label: enums.clearanceReasons[value],
              }))}
              {...text("clearanceReason")}
            />
            <Select
              label={labels.price.visibility}
              options={(["PUBLIC", "CONFIDENTIAL"] as const).map((value) => ({
                value,
                label: enums.visibility[value],
              }))}
              {...text("visibility")}
            />
            <Input
              label={labels.price.notes}
              maxLength={1000}
              {...text("notes")}
            />
          </>
        )}

        {step === "save" && (
          <>
            <p className="text-muted">{labels.saveHint}</p>
            <FormMessage state={state} errors={props.errors} />
          </>
        )}
      </div>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
        {stepIndex > 0 ? (
          <Button
            type="button"
            variant="outline"
            onClick={() => goTo(stepIndex - 1)}
          >
            <ArrowLeft aria-hidden="true" className="size-5" />
            {common.back}
          </Button>
        ) : (
          <span />
        )}
        {step === "save" ? (
          <Button
            type="button"
            variant="accent"
            disabled={pending}
            onClick={submit}
          >
            {labels.save}
          </Button>
        ) : (
          <Button type="button" onClick={next}>
            {common.next}
            <ArrowRight aria-hidden="true" className="size-5" />
          </Button>
        )}
      </div>
    </form>
  );
}

type TextBinder = (key: keyof DraftValues & string) => {
  id: string;
  name: string;
  value: string;
  error?: string;
  onChange: (event: { target: { value: string } }) => void;
};

function ProductStep({
  labels,
  enums,
  categories,
  variants,
  values,
  set,
  text,
}: ListingWizardProps & {
  values: DraftValues;
  set: (patch: Partial<DraftValues>) => void;
  text: TextBinder;
  errorText: (key: string) => string | undefined;
}) {
  const modes = [
    {
      value: "existing",
      label: labels.product.existing,
      disabled: variants.length === 0,
    },
    { value: "new", label: labels.product.new, disabled: false },
  ] as const;

  return (
    <>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 font-semibold">
          {labels.product.modeLegend}
        </legend>
        {modes.map((mode) => (
          <label
            key={mode.value}
            className={cn(
              "flex min-h-12 items-center gap-3 rounded-control border-2 border-border px-4",
              mode.disabled && "opacity-60",
            )}
          >
            <input
              type="radio"
              name="productMode"
              value={mode.value}
              checked={values.productMode === mode.value}
              disabled={mode.disabled}
              onChange={() => set({ productMode: mode.value })}
              className="size-5"
            />
            {mode.label}
          </label>
        ))}
        {variants.length === 0 && (
          <p className="text-sm text-muted">{labels.product.noProducts}</p>
        )}
      </fieldset>

      {values.productMode === "existing" ? (
        <Select
          label={labels.product.choose}
          placeholder={labels.product.choosePlaceholder}
          options={variants}
          {...text("variantId")}
        />
      ) : (
        <>
          <Select
            label={labels.product.category}
            placeholder={labels.product.categoryPlaceholder}
            options={categories}
            {...text("categoryId")}
          />
          <Input
            label={labels.product.brand}
            maxLength={100}
            {...text("brand")}
          />
          <Input
            label={labels.product.name}
            hint={labels.product.nameHint}
            maxLength={200}
            {...text("name")}
          />
          <Input
            inputMode="decimal"
            label={labels.product.packSize}
            autoComplete="off"
            {...text("packSize")}
          />
          <Select
            label={labels.product.packUnit}
            options={PACK_UNITS.map((value) => ({
              value,
              label: enums.packUnits[value],
            }))}
            {...text("packUnit")}
          />
          <Input
            inputMode="numeric"
            label={labels.product.gtin}
            hint={labels.product.gtinHint}
            maxLength={20}
            autoComplete="off"
            {...text("gtin")}
          />
        </>
      )}
    </>
  );
}
