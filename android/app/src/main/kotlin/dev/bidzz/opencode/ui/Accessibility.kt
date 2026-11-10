package dev.bidzz.opencode.ui

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Announces [text] to accessibility services, once.
 *
 * Call only on a transition (a run finishing or failing, a permission
 * arriving, a copy landing) or a user action — never on every
 * recomposition, and never for progress: a screen reader must not narrate
 * the stream. This is the platform `announceForAccessibility` pattern
 * (TYPE_ANNOUNCEMENT); it is a no-op when accessibility is off.
 */
fun announceForAccessibility(context: Context, text: String) {
    if (text.isBlank()) return
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return
    if (!manager.isEnabled) return
    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
        className = "Announcement"
        packageName = context.packageName
        this.text.add(text)
    }
    manager.sendAccessibilityEvent(event)
}
