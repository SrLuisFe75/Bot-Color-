package com.icon.nexus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.icon.nexus.language.ENGLISH_TAG
import com.icon.nexus.language.LANGUAGE_SUPPORT
import com.icon.nexus.language.LanguageChoice
import com.icon.nexus.language.SPANISH_TAG
import com.icon.nexus.language.languageChoice

@Composable
fun LanguageChoices(
    languageTag: String,
    onLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choice = languageChoice(languageTag)
    Text(
        text = LANGUAGE_SUPPORT,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
        modifier = modifier.semantics { contentDescription = LANGUAGE_SUPPORT },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        LanguageOption(
            label = "Auto",
            selected = choice == LanguageChoice.Auto,
            onClick = { onLanguage("") },
        )
        LanguageOption(
            label = "Español",
            selected = choice == LanguageChoice.Spanish,
            onClick = { onLanguage(SPANISH_TAG) },
        )
        LanguageOption(
            label = "English",
            selected = choice == LanguageChoice.English,
            onClick = { onLanguage(ENGLISH_TAG) },
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 6.dp),
    )
}
