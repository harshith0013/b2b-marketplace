/**
 * The search assistant: turns a shopper's question (English, Telugu, Hindi or mixed) into search
 * filters, through any OpenAI-compatible chat API (Google Gemini by default). The model only
 * proposes filters; it never sees or writes products, prices or stock, and nothing it writes is
 * shown to anyone. Callers validate every proposed value and run the normal search.
 *
 * <p>Module boundary: other modules may call only this module's public service API, never its
 * repositories or entities.
 */
package com.b2bmarket.ai;
