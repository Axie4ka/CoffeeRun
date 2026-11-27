package ru.coffeerun.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.coffeerun.R
import ru.coffeerun.model.MAX_RECIPE_NAME_LENGTH
import ru.coffeerun.model.Recipe
import ru.coffeerun.ui.components.EmptyState
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.OnLocale
import ru.coffeerun.ui.util.optionsText

@Composable
fun RecipesRoute(
    onEditRecipe: (Recipe) -> Unit,
    onBrowseMenu: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val vm: RecipesViewModel = viewModel(factory = RecipesViewModel.Factory)
    OnLocale(vm::onLocale)
    val list by vm.list.collectAsStateWithLifecycle()
    val menu by vm.recipeMenu.collectAsStateWithLifecycle()
    val failed by vm.refreshFailed.collectAsStateWithLifecycle()
    val readFailed by vm.readFailed.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(resources.getString(it))
            vm.onMessageShown()
        }
    }
    val session = list.session
    var renameId by rememberSaveable(session?.generation) { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable(session?.generation) { mutableStateOf<String?>(null) }
    val rename = list.recipes.firstOrNull { it.id == renameId }
    val delete = list.recipes.firstOrNull { it.id == deleteId }
    if (session != null && rename != null) {
        RecipeNameDialog(
            title = stringResource(R.string.recipe_rename),
            initialName = rename.name,
            onDismiss = { renameId = null },
            onConfirm = { name ->
                renameId = null
                vm.rename(rename, name, session)
            }
        )
    }
    if (session != null && delete != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_prompt, delete.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteId = null
                    vm.remove(delete, session)
                }) { Text(stringResource(R.string.cart_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.recipe_cancel)) }
            }
        )
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (session != null) {
                TextButton(onClick = vm::retry, enabled = !refreshing) {
                    Text(stringResource(when {
                        failed || readFailed -> R.string.recipe_sync_failed
                        menu == null && list.recipes.isNotEmpty() -> R.string.recipe_menu_unavailable
                        else -> R.string.recipe_refresh
                    }))
                }
                if (refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                list.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.recipes.isEmpty() || session == null ->
                    EmptyState(stringResource(R.string.recipes_empty), onBrowseMenu, Modifier.fillMaxSize())
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = MaterialTheme.spacing.large,
                        top = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.large + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    items(list.recipes, key = Recipe::id) { recipe ->
                        val product = menu?.snapshot?.products?.firstOrNull { it.id == recipe.productId }
                        val price = menu?.portionPrice(recipe)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(MaterialTheme.spacing.large)) {
                                Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                                if (product != null) Text(product.name)
                                val options = optionsText(recipe.options, "")
                                if (options.isNotBlank()) Text(options, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (price != null) stringResource(R.string.price_rub, price)
                                    else stringResource(R.string.recipe_unavailable),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                FlowRow {
                                    TextButton(onClick = { vm.add(recipe, session) }, enabled = price != null && !busy) {
                                        Text(stringResource(R.string.recipe_add_one))
                                    }
                                    TextButton(onClick = { onEditRecipe(recipe) }, enabled = product != null && !busy) {
                                        Text(stringResource(R.string.recipe_edit))
                                    }
                                    TextButton(onClick = { renameId = recipe.id }, enabled = !busy) {
                                        Text(stringResource(R.string.recipe_rename))
                                    }
                                    TextButton(onClick = { deleteId = recipe.id }, enabled = !busy) {
                                        Text(stringResource(R.string.cart_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun RecipeNameDialog(title: String, initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_RECIPE_NAME_LENGTH) name = it },
                label = { Text(stringResource(R.string.recipe_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text(stringResource(R.string.recipe_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_cancel)) }
        }
    )
}
