/**
 * The compliance engine: versioned rule sets that decide whether stock may be listed, and that flag
 * what a person must look at. Every evaluation is stored (append-only) with its rule set version
 * and stage. Rules and AI readings flag problems; they never certify authenticity or compliance.
 * The rules themselves are in {@code PilotRules}, written to be read by counsel.
 *
 * <p>Module boundary: other modules may call only this module's public service API, never its
 * repositories or entities.
 */
package com.b2bmarket.compliance;
