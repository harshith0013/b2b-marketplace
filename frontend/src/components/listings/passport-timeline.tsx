import type { Dictionary } from "@/i18n/dictionaries";
import type { Passport } from "@/lib/api/types";

/** The Batch Passport: what happened, oldest first, with rule results in plain words. */
export function PassportTimeline({
  passport,
  lang,
  labels,
  enums,
}: {
  passport: Passport;
  lang: string;
  labels: Dictionary["listings"];
  enums: Dictionary["labels"];
}) {
  if (passport.entries.length === 0)
    return <p className="text-muted">{labels.passportEmpty}</p>;
  const when = new Intl.DateTimeFormat(`${lang}-IN`, {
    dateStyle: "medium",
    timeStyle: "short",
  });
  const events = enums.events as Record<string, string>;
  const messages: Record<string, string> = {
    ...enums.problems,
    ...enums.flags,
    ...enums.labelOutcomes,
  };
  return (
    <ol className="flex flex-col gap-3 border-l-2 border-border pl-4">
      {passport.entries.map((entry, index) => (
        <li
          key={`${entry.at}-${entry.kind}-${index}`}
          className="flex flex-col gap-1"
        >
          <span className="text-sm text-muted">
            <time dateTime={entry.at}>{when.format(new Date(entry.at))}</time>
          </span>
          <span className="font-semibold">
            {events[entry.kind] ?? entry.kind}
          </span>
          {entry.kind.startsWith("check.") && entry.codes.length === 0 && (
            <span className="text-sm text-success">
              {labels.allRulesPassed}
            </span>
          )}
          {entry.codes.length > 0 && (
            <ul className="text-sm">
              {entry.codes.map((code) => (
                <li key={code}>{messages[code] ?? code}</li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ol>
  );
}
