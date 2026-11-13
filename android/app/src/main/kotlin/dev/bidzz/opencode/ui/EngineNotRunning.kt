package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.api.ApiResult

/**
 * The failure for a load that never reached the engine because there is no
 * engine handle (`EngineApi.handle()` returned null — the engine is not
 * running or has not attached yet).
 *
 * Code 0 is the value the API layer already uses for "no network at all"
 * (`ApiResult.Failure.isNetwork`), so `message()` words it as
 * `error_unreachable` ("Cannot reach the engine") in the app's language —
 * never as an empty list or a "nothing here" text. Every Tier-3 view model
 * reports this from its handle-null branch instead of silently clearing
 * `loading` (audit-100 64-F12, 64-F1, 65-F7, 64-F14).
 */
internal fun engineDownFailure(): ApiResult.Failure = ApiResult.Failure(code = 0)
