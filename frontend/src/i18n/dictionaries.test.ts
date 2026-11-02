import { describe, expect, it } from "vitest";
import en from "./dictionaries/en.json";
import hi from "./dictionaries/hi.json";
import te from "./dictionaries/te.json";

function keyPaths(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) return [prefix];
  return Object.entries(value).flatMap(([key, child]) =>
    keyPaths(child, prefix ? `${prefix}.${key}` : key),
  );
}

function emptyValues(value: unknown, prefix = ""): string[] {
  if (typeof value === "string") return value.trim() === "" ? [prefix] : [];
  if (typeof value !== "object" || value === null) return [];
  return Object.entries(value).flatMap(([key, child]) =>
    emptyValues(child, prefix ? `${prefix}.${key}` : key),
  );
}

describe("translation dictionaries", () => {
  const reference = keyPaths(en).sort();

  it.each([
    ["te", te],
    ["hi", hi],
  ])("%s has exactly the same keys as English", (_name, dict) => {
    expect(keyPaths(dict).sort()).toEqual(reference);
  });

  it.each([
    ["en", en],
    ["te", te],
    ["hi", hi],
  ])("%s has no empty strings", (_name, dict) => {
    expect(emptyValues(dict)).toEqual([]);
  });
});
