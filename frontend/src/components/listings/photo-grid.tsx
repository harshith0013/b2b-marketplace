import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { Photo } from "@/lib/api/types";

/**
 * Photos served through this site's own routes (the token stays on the server). Plain <img>: the
 * images are private, so they must not go through a public image optimiser.
 */
export function PhotoGrid({
  photos,
  srcOf,
  labels,
  kinds,
  deleteActionOf,
}: {
  photos: Photo[];
  srcOf: (photo: Photo) => string;
  labels: Pick<Dictionary["listings"], "noPhotos" | "photoAlt" | "deletePhoto">;
  kinds: Dictionary["labels"]["photoKinds"];
  /** Present when photos may be deleted (a draft, for owners and managers). */
  deleteActionOf?: (photo: Photo) => () => Promise<void>;
}) {
  if (photos.length === 0)
    return <p className="text-muted">{labels.noPhotos}</p>;
  return (
    <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {photos.map((photo) => (
        <li
          key={photo.id}
          className="flex flex-col gap-2 rounded-card border-2 border-border p-2"
        >
          <a href={srcOf(photo)} target="_blank" rel="noopener">
            {/* eslint-disable-next-line @next/next/no-img-element -- private, uncached images */}
            <img
              src={srcOf(photo)}
              alt={format(labels.photoAlt, { kind: kinds[photo.kind] })}
              width={photo.width}
              height={photo.height}
              loading="lazy"
              className="aspect-square w-full rounded-control object-cover"
            />
          </a>
          <span className="text-sm font-semibold">{kinds[photo.kind]}</span>
          {deleteActionOf && (
            <form action={deleteActionOf(photo)}>
              <Button type="submit" variant="ghost" className="w-full">
                <Trash2 aria-hidden="true" className="size-5" />
                {labels.deletePhoto}
                <span className="sr-only">, {kinds[photo.kind]}</span>
              </Button>
            </form>
          )}
        </li>
      ))}
    </ul>
  );
}
