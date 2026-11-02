-- Phase 1 baseline. Business tables arrive from Phase 3 onwards, one migration per change.
-- Never edit an applied migration; add a new one.

-- Trigram indexes for fuzzy product-name search (Phase 6).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Vector similarity search for semantic product search, used only where it
-- measurably beats full-text search (Phase 7).
CREATE EXTENSION IF NOT EXISTS vector;
