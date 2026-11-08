/** Whether search engines may index the site; off until the pilot (set SITE_INDEXABLE=true). */
export function siteIndexable(): boolean {
  return process.env.SITE_INDEXABLE === "true";
}
