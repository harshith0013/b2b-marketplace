import { Badge } from "@/components/ui/badge";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";

interface SampleOfferCardProps {
  name: string;
  detail: string;
  seller: string;
  sampleLabel: string;
}

/** A placeholder offer. Always carries a visible "sample data" badge. */
export function SampleOfferCard({ name, detail, seller, sampleLabel }: SampleOfferCardProps) {
  return (
    <Card className="flex flex-col gap-2">
      <Badge variant="warning" className="self-start">
        {sampleLabel}
      </Badge>
      <CardTitle>{name}</CardTitle>
      <CardDescription>{detail}</CardDescription>
      <p className="text-sm">{seller}</p>
    </Card>
  );
}
