package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.engine.EngineCredentials

/**
 * Whether the attached handle points at a different engine than [next].
 *
 * Every restart prints a new password and the supervisor allocates a fresh ephemeral
 * port, so either field changing means every client built from the old handle — REST
 * and the event stream alike — is talking to a process that no longer exists. A port
 * can be reused between the moment the old engine dies and the new one binds, so
 * comparing the base URL alone would let a stale password survive as permanent 401s.
 */
fun isStale(attached: EngineCredentials?, next: EngineCredentials): Boolean = attached != next
