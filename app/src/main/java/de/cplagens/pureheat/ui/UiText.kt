package de.cplagens.pureheat.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text that is resolved in the UI, so it always appears in the current app language (also
 * after switching the language at runtime). ViewModels and the data layer create these
 * instead of finished strings. Arguments may themselves be [UiText].
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    data class Plain(val text: String) : UiText

    /** Several texts joined with [separator]. */
    data class Join(val parts: List<UiText>, val separator: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Plain -> text
        is Join -> parts.joinToString(separator) { it.resolve(context) }
        is Res -> context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    }
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
