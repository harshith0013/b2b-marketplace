-- Phase 4: the shared catalogue. Categories (seeded), products and their pack variants.
-- Pilot category: non-food FMCG, so nothing seeded here is regulated. Regulated categories
-- (food, pest control, medicines) arrive with the compliance engine in Phase 8.

CREATE TABLE categories (
    id         uuid PRIMARY KEY,
    parent_id  uuid REFERENCES categories (id),
    slug       varchar(80)  NOT NULL UNIQUE,
    name_en    varchar(100) NOT NULL,
    name_te    varchar(100) NOT NULL,
    name_hi    varchar(100) NOT NULL,
    regulated  boolean      NOT NULL DEFAULT false,
    active     boolean      NOT NULL DEFAULT true,
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT categories_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT categories_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX categories_parent_id_idx ON categories (parent_id);

CREATE TABLE products (
    id                     uuid PRIMARY KEY,
    category_id            uuid          NOT NULL REFERENCES categories (id),
    brand                  varchar(100)  NOT NULL,
    name                   varchar(200)  NOT NULL,
    description            varchar(2000),
    -- New products are visible only to the business that added them until an admin approves
    -- them (approval queue: Phase 5).
    review_status          varchar(20)   NOT NULL DEFAULT 'UNREVIEWED',
    created_by_business_id uuid REFERENCES businesses (id),
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    CONSTRAINT products_review_status_check CHECK (review_status IN
        ('UNREVIEWED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX products_category_id_idx ON products (category_id);
CREATE INDEX products_created_by_business_id_idx ON products (created_by_business_id);

-- One sellable pack of a product, e.g. "Rin detergent bar, 250 g".
CREATE TABLE product_variants (
    id         uuid PRIMARY KEY,
    product_id uuid           NOT NULL REFERENCES products (id),
    pack_size  numeric(12, 3) NOT NULL,
    pack_unit  varchar(10)    NOT NULL,
    gtin       varchar(14) UNIQUE, -- Barcode (GTIN-8/12/13/14); check digit verified by the API.
    created_at timestamptz    NOT NULL,
    updated_at timestamptz    NOT NULL,
    CONSTRAINT product_variants_pack_size_positive CHECK (pack_size > 0),
    CONSTRAINT product_variants_pack_unit_check CHECK (pack_unit IN ('G', 'KG', 'ML', 'L', 'PIECE')),
    CONSTRAINT product_variants_gtin_format CHECK (gtin ~ '^([0-9]{8}|[0-9]{12,14})$'),
    CONSTRAINT product_variants_pack_key UNIQUE (product_id, pack_size, pack_unit)
);

-- Seed: the non-food FMCG pilot tree. Telugu and Hindi names need native-speaker review.
INSERT INTO categories (id, parent_id, slug, name_en, name_te, name_hi, sort_order, created_at, updated_at)
VALUES
    (gen_random_uuid(), NULL, 'personal-care', 'Personal care', 'వ్యక్తిగత సంరక్షణ', 'व्यक्तिगत देखभाल', 10,
     '2026-11-05T00:00:00Z', '2026-11-05T00:00:00Z'),
    (gen_random_uuid(), NULL, 'home-care', 'Home care', 'గృహ సంరక్షణ', 'घर की देखभाल', 20,
     '2026-11-05T00:00:00Z', '2026-11-05T00:00:00Z'),
    (gen_random_uuid(), NULL, 'household-kitchen', 'Household and kitchen', 'గృహోపకరణాలు, వంటగది', 'घरेलू और रसोई', 30,
     '2026-11-05T00:00:00Z', '2026-11-05T00:00:00Z'),
    (gen_random_uuid(), NULL, 'stationery', 'Stationery', 'స్టేషనరీ', 'स्टेशनरी', 40,
     '2026-11-05T00:00:00Z', '2026-11-05T00:00:00Z');

INSERT INTO categories (id, parent_id, slug, name_en, name_te, name_hi, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), parent.id, child.slug, child.name_en, child.name_te, child.name_hi, child.sort_order,
       '2026-11-05T00:00:00Z', '2026-11-05T00:00:00Z'
FROM (VALUES
    ('personal-care', 'bath-soaps', 'Bath soaps and body wash', 'స్నానపు సబ్బులు, బాడీ వాష్', 'नहाने का साबुन और बॉडी वॉश', 10),
    ('personal-care', 'hair-care', 'Hair care', 'జుట్టు సంరక్షణ', 'बालों की देखभाल', 20),
    ('personal-care', 'oral-care', 'Oral care', 'నోటి సంరక్షణ', 'मुँह की देखभाल', 30),
    ('personal-care', 'skin-care', 'Skin care', 'చర్మ సంరక్షణ', 'त्वचा की देखभाल', 40),
    ('personal-care', 'shaving-grooming', 'Shaving and grooming', 'షేవింగ్, గ్రూమింగ్', 'शेविंग और ग्रूमिंग', 50),
    ('home-care', 'laundry', 'Laundry detergents', 'బట్టల డిటర్జెంట్లు', 'कपड़े धोने का डिटर्जेंट', 10),
    ('home-care', 'dishwash', 'Dishwash', 'పాత్రలు కడిగే సబ్బులు', 'बर्तन धोने का साबुन', 20),
    ('home-care', 'surface-cleaners', 'Floor and surface cleaners', 'నేల, ఉపరితల క్లీనర్లు', 'फ़र्श और सतह क्लीनर', 30),
    ('home-care', 'air-fresheners', 'Air fresheners', 'ఎయిర్ ఫ్రెషనర్లు', 'एयर फ्रेशनर', 40),
    ('household-kitchen', 'kitchen-supplies', 'Foil, wraps and bags', 'ఫాయిల్, ర్యాప్‌లు, సంచులు', 'फ़ॉइल, रैप और थैलियाँ', 10),
    ('household-kitchen', 'disposables', 'Disposable plates and cups', 'డిస్పోజబుల్ ప్లేట్లు, కప్పులు', 'डिस्पोज़ेबल प्लेट और कप', 20),
    ('stationery', 'notebooks-paper', 'Notebooks and paper', 'నోట్‌బుక్‌లు, కాగితం', 'कॉपियाँ और कागज़', 10),
    ('stationery', 'pens-writing', 'Pens and writing', 'పెన్నులు, రాత సామగ్రి', 'पेन और लेखन सामग्री', 20)
) AS child (parent_slug, slug, name_en, name_te, name_hi, sort_order)
JOIN categories parent ON parent.slug = child.parent_slug;
