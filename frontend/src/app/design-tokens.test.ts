// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const css = readFileSync(
  fileURLToPath(new URL("./globals.css", import.meta.url)),
  "utf8",
);

const AA_TEXT = 4.5;
const AA_NON_TEXT = 3;

/** Reads `--name: #hex;` declarations from the first block that follows `marker`. */
function tokensAfter(marker: string): Record<string, string> {
  const start = css.indexOf(marker);
  const block = css.slice(start, css.indexOf("}", start));
  return Object.fromEntries(
    [...block.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6});/gi)].map(
      ([, name, hex]) => [name, hex],
    ),
  );
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = Number.parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const textPairs: [string, string][] = [
  ["foreground", "background"],
  ["foreground", "surface"],
  ["muted", "background"],
  ["muted", "surface"],
  ["background", "foreground"],
  ["accent-foreground", "accent"],
  ["danger", "background"],
  ["success", "success-soft"],
  ["danger", "danger-soft"],
  ["warning", "warning-soft"],
  ["info", "info-soft"],
];

const nonTextPairs: [string, string][] = [
  ["border-strong", "background"],
  ["border-strong", "surface"],
  ["ring", "background"],
  ["ring", "surface"],
];

describe.each([
  ["light", tokensAfter(":root {")],
  ["dark", tokensAfter("@media (prefers-color-scheme: dark)")],
])("%s theme", (_theme, tokens) => {
  it.each(textPairs)("%s on %s meets AA for text", (fg, bg) => {
    expect(contrast(tokens[fg], tokens[bg])).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it.each(nonTextPairs)("%s on %s meets AA for UI components", (fg, bg) => {
    expect(contrast(tokens[fg], tokens[bg])).toBeGreaterThanOrEqual(
      AA_NON_TEXT,
    );
  });
});
