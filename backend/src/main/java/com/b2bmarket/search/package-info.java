/**
 * Public offer discovery: full-text and typo-tolerant search with filters over published, approved
 * stock. Read-only, anonymous; queries run as the marketplace_public database role. Semantic search
 * arrives in Phase 7.
 *
 * <p>Module boundary: other modules may call only this module's public service API, never its
 * repositories or entities.
 */
package com.b2bmarket.search;
