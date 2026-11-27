package ru.coffeerun.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ru.coffeerun.R
import ru.coffeerun.ui.theme.spacing

@Composable
internal fun AvatarImage(model: Any?, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val initial = name.trim().firstOrNull()?.uppercase()
        if (initial == null) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        } else {
            Text(
                initial,
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.avatar_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun AvatarEditor(
    model: Any?,
    name: String,
    onPicked: (String) -> Unit,
    onRemoved: () -> Unit,
    enabled: Boolean,
    isWorking: Boolean,
    optional: Boolean = false
) {
    val picker = if (LocalInspectionMode.current) null else rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri.toString())
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(model, name)
        Column(Modifier.weight(1f)) {
            TextButton(
                onClick = { picker?.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                enabled = enabled && !isWorking
            ) {
                Text(stringResource(when {
                    model != null -> R.string.avatar_change
                    optional -> R.string.avatar_add_optional
                    else -> R.string.avatar_add
                }))
            }
            if (model != null) {
                TextButton(onClick = onRemoved, enabled = enabled && !isWorking) {
                    Text(stringResource(R.string.avatar_remove))
                }
            }
        }
        if (isWorking) CircularProgressIndicator(Modifier.size(24.dp))
    }
}
