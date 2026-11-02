import * as React from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface HomeTileProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  /** Where the tile leads. Without it the tile is shown as not yet available. */
  href?: string;
  comingSoonLabel: string;
}

const tileClassName =
  "flex h-full min-h-28 items-start gap-4 rounded-card border-2 p-4 [&_svg]:size-7 [&_svg]:shrink-0";

export function HomeTile({ icon, title, description, href, comingSoonLabel }: HomeTileProps) {
  const content = (
    <>
      <span aria-hidden="true" className="rounded-control bg-surface p-2.5 text-foreground">
        {icon}
      </span>
      <span className="flex flex-col gap-1">
        <span className="text-title font-bold">{title}</span>
        <span className="text-muted">{description}</span>
        {!href && <Badge className="mt-1 self-start">{comingSoonLabel}</Badge>}
      </span>
    </>
  );

  if (href) {
    return (
      <Link
        href={href}
        className={cn(tileClassName, "border-accent bg-background shadow-card hover:bg-surface")}
      >
        {content}
      </Link>
    );
  }
  return <div className={cn(tileClassName, "border-border bg-background")}>{content}</div>;
}
