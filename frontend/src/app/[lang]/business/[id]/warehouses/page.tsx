import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, Warehouse as WarehouseIcon } from "lucide-react";
import { WarehouseForm } from "@/components/warehouses/warehouse-form";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { createWarehouse, setWarehouseActive } from "@/lib/actions/warehouses";
import { ApiError } from "@/lib/api/backend";
import type { BusinessDetails, Warehouse } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Params = PageProps<"/[lang]/business/[id]/warehouses">["params"];

/** Everything here depends on the business id, which is only known per request. */
async function WarehousesContent({ params }: { params: Params }) {
  const { lang, id } = await params;
  if (!hasLocale(lang) || !UUID.test(id)) notFound();
  const dict = await getDictionary(lang);
  const labels = dict.warehouses;
  const returnTo = `/${lang}/business/${id}/warehouses`;
  const session = await requireSession(lang, returnTo);

  let business: BusinessDetails;
  let warehouses: Warehouse[];
  try {
    [business, warehouses] = await Promise.all([
      api<BusinessDetails>(session, lang, returnTo, `/api/v1/businesses/${id}`),
      api<Warehouse[]>(session, lang, returnTo, `/api/v1/businesses/${id}/warehouses`),
    ]);
  } catch (error) {
    // Not a member: the API says 404, and so do we.
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
  const canEdit = business.yourRole === "OWNER" || business.yourRole === "MANAGER";

  return (
    <>
      <div className="flex flex-col gap-3">
        <Link
          href={`/${lang}/business/${id}`}
          className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
        >
          <ArrowLeft aria-hidden="true" className="size-5" />
          {labels.back}
        </Link>
        <h1 className="text-display font-bold">{labels.title}</h1>
        <p className="text-muted">
          {business.tradeName ?? business.legalName} · {labels.intro}
        </p>
      </div>

      <section aria-labelledby="warehouses-heading" className="flex flex-col gap-3">
        <h2 id="warehouses-heading" className="text-heading font-bold">
          {labels.listHeading}
        </h2>
        {warehouses.length === 0 ? (
          <EmptyState icon={<WarehouseIcon />} title={labels.none} />
        ) : (
          <ul className="flex flex-col gap-2">
            {warehouses.map((warehouse) => (
              <li
                key={warehouse.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-card border-2 border-border p-4"
              >
                <span className="flex flex-col gap-1">
                  <span className="font-semibold">{warehouse.name}</span>
                  <span className="text-sm text-muted">
                    {warehouse.addressLine}, {warehouse.city}, {warehouse.state} {warehouse.pincode}
                  </span>
                </span>
                <span className="flex flex-wrap items-center gap-2">
                  <Badge variant={warehouse.active ? "success" : "warning"}>
                    {warehouse.active ? labels.active : labels.inactive}
                  </Badge>
                  {canEdit && (
                    <form
                      action={setWarehouseActive.bind(null, lang, id, warehouse.id, !warehouse.active)}
                    >
                      <Button type="submit" variant="outline">
                        {warehouse.active ? labels.deactivate : labels.activate}
                        <span className="sr-only">, {warehouse.name}</span>
                      </Button>
                    </form>
                  )}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {canEdit ? (
        <section aria-labelledby="add-warehouse-heading" className="flex flex-col gap-3">
          <h2 id="add-warehouse-heading" className="text-heading font-bold">
            {labels.addHeading}
          </h2>
          <Card>
            <WarehouseForm
              labels={labels}
              errors={dict.business.errors}
              action={createWarehouse.bind(null, lang, id)}
            />
          </Card>
        </section>
      ) : (
        <p className="text-muted">{labels.readOnly}</p>
      )}
    </>
  );
}

export default function WarehousesPage({ params }: PageProps<"/[lang]/business/[id]/warehouses">) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <WarehousesContent params={params} />
      </Suspense>
    </div>
  );
}
