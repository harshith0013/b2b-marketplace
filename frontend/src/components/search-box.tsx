"use client";

import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { controlClassName } from "@/components/ui/field";
import { useToast } from "@/components/ui/toast";
import type { Dictionary } from "@/i18n/dictionaries";
import { cn } from "@/lib/utils";

type SearchLabels = Pick<
  Dictionary["home"],
  "searchLabel" | "searchPlaceholder" | "searchButton" | "searchComingSoon"
>;

// Search is not connected yet (Phase 6); submitting explains that instead of failing silently.
export function SearchBox({ labels }: { labels: SearchLabels }) {
  const showToast = useToast();

  return (
    <form
      role="search"
      className="flex flex-col gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        showToast(labels.searchComingSoon);
      }}
    >
      <label htmlFor="product-search" className="font-semibold">
        {labels.searchLabel}
      </label>
      <div className="flex gap-2">
        <input
          id="product-search"
          name="q"
          type="search"
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
