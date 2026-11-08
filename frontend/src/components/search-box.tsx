import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { controlClassName } from "@/components/ui/field";
import type { Dictionary } from "@/i18n/dictionaries";
import { cn } from "@/lib/utils";

type SearchLabels = Pick<
  Dictionary["home"],
  "searchLabel" | "searchPlaceholder" | "searchButton"
>;

/** A plain GET form to the offers page: works before (and without) any JavaScript. */
export function SearchBox({
  lang,
  labels,
  defaultValue,
}: {
  lang: string;
  labels: SearchLabels;
  defaultValue?: string;
}) {
  return (
    <form
      role="search"
      action={`/${lang}/offers`}
      method="get"
      className="flex flex-col gap-2"
    >
      <label htmlFor="product-search" className="font-semibold">
        {labels.searchLabel}
      </label>
      <div className="flex gap-2">
        <input
          id="product-search"
          name="q"
          type="search"
          maxLength={100}
          defaultValue={defaultValue}
          placeholder={labels.searchPlaceholder}
          className={cn(controlClassName, "flex-1")}
        />
        <Button type="submit" variant="accent">
          <Search aria-hidden="true" className="size-5" />
          <span className="sr-only sm:not-sr-only">{labels.searchButton}</span>
        </Button>
      </div>
    </form>
  );
}
