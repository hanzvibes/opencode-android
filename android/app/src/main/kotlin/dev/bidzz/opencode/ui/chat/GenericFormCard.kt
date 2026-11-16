package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.FormField
import dev.bidzz.opencode.core.api.dto.FormInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.SelectorChip
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A card for one pending generic form (audit-200 C17, M-263).
 *
 * Question forms already have their card in the transcript; a generic
 * form — an agent `forms.ask()` call, an MCP elicitation, an OAuth
 * prompt — has no card anywhere else, so without this the run waits on
 * an answer the user can never give. The card sits above the composer,
 * next to the inbox chip.
 *
 * Fields render by type: text (or option chips when the string field
 * offers choices), numeric input, a switch, multi-select chips, or an
 * "Open link" button for an external field. Hidden fields are skipped
 * entirely — the engine uses their default. `when` conditions are NOT
 * evaluated in v1: a conditional field simply shows. Submit stays
 * disabled while a required field is unanswered or a number does not
 * parse; the answers go back keyed by field key with typed JSON values.
 */
@Composable
fun GenericFormCard(
    form: FormInfo,
    busy: Boolean,
    onSubmit: (Map<String, JsonElement>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    // Visible fields only — hidden ones are never rendered or answered.
    val fields = remember(form) { form.fields.filter { !it.hidden } }
    // Text answers, keyed by field key; pre-filled from string/number
    // defaults so a pre-filled field reads as answered.
    val texts = remember(form) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { field ->
                (field.default as? JsonPrimitive)?.contentOrNull?.let { put(field.key, it) }
            }
        }
    }
    val booleans = remember(form) {
        mutableStateMapOf<String, Boolean>().apply {
            fields.forEach { field ->
                if (field.type == "boolean") {
                    put(field.key, (field.default as? JsonPrimitive)?.booleanOrNull ?: false)
                }
            }
        }
    }
    val singles = remember(form) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { field ->
                if (field.type == "string" && field.options.isNotEmpty()) {
                    (field.default as? JsonPrimitive)?.contentOrNull?.let { put(field.key, it) }
                }
            }
        }
    }
    val multis = remember(form) {
        mutableStateMapOf<String, Set<String>>().apply {
            fields.forEach { field ->
                if (field.type == "multiselect") {
                    val defaults = (field.default as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        ?.toSet()
                        ?: emptySet()
                    put(field.key, defaults)
                }
            }
        }
    }

    // Submit is blocked while a required field is unanswered or a
    // number does not parse — the engine would reject the reply anyway.
    val valid = fields.all { field ->
        when (field.type) {
            "boolean", "external" -> true
            "multiselect" -> !field.required || (multis[field.key]?.isNotEmpty() == true)
            "string" -> {
                if (field.options.isNotEmpty()) {
                    !field.required || singles.containsKey(field.key)
                } else {
                    !field.required || texts[field.key]?.isNotBlank() == true
                }
            }
            "number" -> {
                val text = texts[field.key].orEmpty()
                (!field.required || text.isNotBlank()) &&
                    (text.isBlank() || text.toDoubleOrNull() != null)
            }
            "integer" -> {
                val text = texts[field.key].orEmpty()
                (!field.required || text.isNotBlank()) &&
                    (text.isBlank() || text.toLongOrNull() != null)
            }
            // An unrecognised type still shows (as text) and answers as
            // a string — a newer engine must not break the card.
            else -> !field.required || texts[field.key]?.isNotBlank() == true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs)
            .background(colors.fill, RoundedCornerShape(radius.md))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = form.title.ifBlank { stringResource(R.string.chat_form_title) },
            style = OpenCodeTypography.label,
            color = colors.onSurface,
        )
        fields.forEach { field ->
            GenericFormField(
                field = field,
                text = texts[field.key].orEmpty(),
                onTextChange = { texts[field.key] = it },
                boolean = booleans[field.key] ?: false,
                onBooleanChange = { booleans[field.key] = it },
                single = singles[field.key],
                onSingleChange = { singles[field.key] = it },
                multi = multis[field.key] ?: emptySet(),
                onMultiChange = { multis[field.key] = it },
                enabled = !busy,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    val answer = buildMap<String, JsonElement> {
                        fields.forEach { field ->
                            when (field.type) {
                                "boolean" -> put(
                                    field.key,
                                    JsonPrimitive(booleans[field.key] ?: false),
                                )
                                "multiselect" -> put(
                                    field.key,
                                    JsonArray((multis[field.key] ?: emptySet()).map { JsonPrimitive(it) }),
                                )
                                "string" -> {
                                    if (field.options.isNotEmpty()) {
                                        singles[field.key]?.let { put(field.key, JsonPrimitive(it)) }
                                    } else {
                                        texts[field.key]?.takeIf { it.isNotBlank() }?.let {
                                            put(field.key, JsonPrimitive(it))
                                        }
                                    }
                                }
                                "number" -> texts[field.key]
                                    ?.toDoubleOrNull()
                                    ?.let { put(field.key, JsonPrimitive(it)) }
                                "integer" -> texts[field.key]
                                    ?.toLongOrNull()
                                    ?.let { put(field.key, JsonPrimitive(it)) }
                                "external" -> Unit
                                else -> texts[field.key]?.takeIf { it.isNotBlank() }?.let {
                                    put(field.key, JsonPrimitive(it))
                                }
                            }
                        }
                    }
                    onSubmit(answer)
                },
                enabled = !busy && valid,
            ) {
                Text(
                    text = stringResource(R.string.chat_form_submit),
                    style = OpenCodeTypography.label,
                    color = colors.accent,
                )
            }
            TextButton(onClick = onCancel, enabled = !busy) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun GenericFormField(
    field: FormField,
    text: String,
    onTextChange: (String) -> Unit,
    boolean: Boolean,
    onBooleanChange: (Boolean) -> Unit,
    single: String?,
    onSingleChange: (String) -> Unit,
    multi: Set<String>,
    onMultiChange: (Set<String>) -> Unit,
    enabled: Boolean,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val title = field.title?.takeIf { it.isNotBlank() } ?: field.key
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(
            text = if (field.required) "$title *" else title,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
        )
        // The description is a public property from another module, so
        // the null check cannot smart-cast it — capture it locally first.
        val description = field.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
            )
        }
        when (field.type) {
            "boolean" -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Switch(
                    checked = boolean,
                    onCheckedChange = onBooleanChange,
                    enabled = enabled,
                )
                Text(
                    text = if (boolean) stringResource(R.string.common_yes)
                    else stringResource(R.string.common_no),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            }
            "multiselect" -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                field.options.forEach { option ->
                    SelectorChip(
                        label = option.label.ifBlank { option.value },
                        selected = option.value in multi,
                        onClick = {
                            onMultiChange(
                                if (option.value in multi) multi - option.value
                                else multi + option.value,
                            )
                        },
                    )
                }
            }
            "external" -> {
                val url = field.url.orEmpty()
                if (url.isNotBlank()) {
                    TextButton(
                        onClick = { uriHandler.openUri(url) },
                        enabled = enabled,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_form_open_link),
                            style = OpenCodeTypography.label,
                            color = colors.accent,
                        )
                    }
                }
            }
            "number", "integer" -> OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = field.placeholder?.let { { Text(text = it) } },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            "string" -> {
                if (field.options.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        field.options.forEach { option ->
                            SelectorChip(
                                label = option.label.ifBlank { option.value },
                                selected = single == option.value,
                                onClick = { onSingleChange(option.value) },
                                // Audit-200 D9 (m-853): one value at a time.
                                role = Role.RadioButton,
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = field.placeholder?.let { { Text(text = it) } },
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // Unknown type: a plain text input answered as a string.
            else -> OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = field.placeholder?.let { { Text(text = it) } },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
