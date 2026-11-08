import { ScanText } from "lucide-react";
import type { Dictionary } from "@/i18n/dictionaries";
import type { BatchSummary, LabelCheck } from "@/lib/api/types";
import { formatPaise } from "@/lib/money";
import { formatDate } from "./listing-facts";

/** "2027-03" → "Mar 2027"; "2027-03-31" → "31 Mar 2027", in the reader's language. */
function formatExpiry(expiry: string, lang: string): string {
  if (/^\d{4}-\d{2}$/.test(expiry)) {
    return new Intl.DateTimeFormat(`${lang}-IN`, {
      month: "short",
      year: "numeric",
      timeZone: "UTC",
    }).format(new Date(`${expiry}-01T00:00:00Z`));
  }
  return formatDate(expiry, lang);
}

/**
 * What was read from the label photo next to what was typed, so a person can compare. Labelled
 * as an automatic aid, never as proof.
 */
export function LabelCheckPanel({
  label,
  batch,
  lang,
  labels,
  enums,
  typedLabel,
  readLabel,
}: {
  label: LabelCheck | null;
  batch: BatchSummary;
  lang: string;
  labels: Dictionary["listings"];
  enums: Dictionary["labels"];
  typedLabel: string;
  readLabel: string;
}) {
  if (!label) {
    return <p className="text-sm text-muted">{labels.labelPending}</p>;
  }
  const rows: [string, string, string][] = [
    [
      labels.labelDate,
      `${enums.dateTypes[batch.dateType]} ${formatDate(batch.expiresOn, lang)}`,
      label.expiry
        ? `${label.dateType ? `${enums.dateTypes[label.dateType]} ` : ""}${formatExpiry(label.expiry, lang)}`
        : labels.notRead,
    ],
    [labels.labelBatch, batch.batchNumber, label.batchNumber ?? labels.notRead],
    [
      labels.labelMrp,
      batch.mrpPaise === null
        ? labels.notGiven
        : formatPaise(batch.mrpPaise, lang),
      label.mrpPaise === null
        ? labels.notRead
        : formatPaise(label.mrpPaise, lang),
    ],
  ];
  return (
    <div className="flex flex-col gap-3">
      <p className="flex items-center gap-2 font-semibold">
        <ScanText aria-hidden="true" className="size-5" />
        {enums.labelOutcomes[label.outcome]}
        {label.printedDate && (
          <span className="font-normal text-muted">“{label.printedDate}”</span>
        )}
      </p>
      {label.outcome === "READ" && (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="text-muted">
              <th scope="col" className="py-1 pr-2 font-semibold">
                <span className="sr-only">{labels.labelHeading}</span>
              </th>
              <th scope="col" className="py-1 pr-2 font-semibold">
                {typedLabel}
              </th>
              <th scope="col" className="py-1 font-semibold">
                {readLabel}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([name, typed, read]) => (
              <tr key={name} className="border-t-2 border-border">
                <th scope="row" className="py-2 pr-2 font-semibold">
                  {name}
                </th>
                <td className="py-2 pr-2">{typed}</td>
                <td className="py-2">{read}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <p className="text-sm text-muted">{labels.labelNote}</p>
    </div>
  );
}
