import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { ListingWizard } from "@/components/listings/listing-wizard";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { saveListingDraft } from "@/lib/actions/listings";
import type {
  BusinessDetails,
  Category,
  PageResult,
  Product,
  Warehouse,
} from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";
import { apiOrNotFound, canEdit, requireUuids } from "@/lib/pages";

type Params = PageProps<"/[lang]/business/[id]/listings/new">["params"];

async function WizardContent({ params }: { params: Params }) {
  const { lang, id } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id);
  const dict = await getDictionary(lang);
  const listingsPath = `/${lang}/business/${id}/listings`;
  const returnTo = `${listingsPath}/new`;
  const session = await requireSession(lang, returnTo);
  const business = await apiOrNotFound<BusinessDetails>(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${id}`,
  );
  const header = (
    <div className="flex flex-col gap-2">
      <Link
        href={listingsPath}
        className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
      >
        <ArrowLeft aria-hidden="true" className="size-5" />
        {dict.listings.back}
      </Link>
      <h1 className="text-display font-bold">{dict.wizard.title}</h1>
      <p className="text-muted">{dict.wizard.intro}</p>
    </div>
  );
  if (!canEdit(business.yourRole)) {
    return (
      <>
        {header}
        <p className="text-muted">{dict.wizard.notAllowed}</p>
      </>
    );
  }
  const [categories, products, warehouses] = await Promise.all([
    api<Category[]>(session, lang, returnTo, "/api/v1/categories"),
    api<PageResult<Product>>(
      session,
      lang,
      returnTo,
      "/api/v1/products?size=50",
    ),
    api<Warehouse[]>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}/warehouses`,
    ),
  ]);

  const nameOf = (c: Category) => c.name[lang];
  const byId = new Map(categories.map((c) => [c.id, c]));
  const categoryOptions = categories
    .filter((c) => c.leaf && !c.regulated)
    .map((c) => {
      const parent = c.parentId ? byId.get(c.parentId) : undefined;
      return {
        value: c.id,
        label: parent ? `${nameOf(parent)} › ${nameOf(c)}` : nameOf(c),
      };
    });
  const variantOptions = products.items.flatMap((p) =>
    p.variants.map((v) => ({
      value: v.id,
      label: `${p.brand} ${p.name}, ${format(dict.listings.pack, {
        size: String(v.packSize),
        unit: dict.labels.packUnits[v.packUnit],
      })}`,
    })),
  );
  const warehouseOptions = warehouses
    .filter((w) => w.active)
    .map((w) => ({ value: w.id, label: `${w.name}, ${w.city}` }));

  return (
    <>
      {header}
      <ListingWizard
        labels={dict.wizard}
        enums={dict.labels}
        errors={dict.business.errors}
        common={dict.common}
        categories={categoryOptions}
        variants={variantOptions}
        warehouses={warehouseOptions}
        addWarehouseHref={`/${lang}/business/${id}/warehouses`}
        action={saveListingDraft.bind(null, lang, id)}
      />
    </>
  );
}

export default function NewListingPage({
  params,
}: PageProps<"/[lang]/business/[id]/listings/new">) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <WizardContent params={params} />
      </Suspense>
    </div>
  );
}
