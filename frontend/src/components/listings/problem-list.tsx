import { CircleAlert, CircleCheck } from "lucide-react";

/** The open checks (or flags) in plain words, or a single "all good" line. */
export function ProblemList({
  problems,
  messages,
  allGood,
}: {
  problems: readonly string[];
  messages: Record<string, string>;
  allGood?: string;
}) {
  if (problems.length === 0) {
    if (!allGood) return null;
    return (
      <p className="flex items-center gap-2 rounded-control bg-success-soft px-4 py-3 font-semibold text-success">
        <CircleCheck aria-hidden="true" className="size-5 shrink-0" />
        {allGood}
      </p>
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {problems.map((problem) => (
        <li
          key={problem}
          className="flex items-start gap-2 rounded-control bg-warning-soft px-4 py-3 font-semibold text-warning"
        >
          <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {messages[problem] ?? problem}
        </li>
      ))}
    </ul>
  );
}
