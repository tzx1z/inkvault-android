package dev.inkov.inkvault.android.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTooltipState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.inkov.inkvault.android.Biometric
import dev.inkov.inkvault.android.BuildConfig
import dev.inkov.inkvault.android.FAVORITE
import dev.inkov.inkvault.android.Item
import dev.inkov.inkvault.android.PASSWORD_MODE
import dev.inkov.inkvault.android.PIN_TOTP
import dev.inkov.inkvault.android.QrAnalyzer
import dev.inkov.inkvault.android.R
import dev.inkov.inkvault.android.Screen
import dev.inkov.inkvault.android.Settings
import dev.inkov.inkvault.android.Status
import dev.inkov.inkvault.android.VaultController
import dev.inkov.inkvault.android.copySecret
import dev.inkov.inkvault.android.scannedSecretProblem
import uniffi.inkvault_uniffi.generatePassword
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import uniffi.inkvault_uniffi.passwordEntropy
import uniffi.inkvault_uniffi.totp

// Диалоги Compose открываются в отдельном окне со своим деревом семантики, поэтому флаг для
// uiautomator ставится на каждый элемент с тегом, а не только на корень экрана
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.tag(name: String): Modifier = semantics { testTagsAsResourceId = true }.testTag(name)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkVaultApp(controller: VaultController, activity: Activity) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        android.os.Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        android.os.Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        // testTag доступен uiautomator как resource-id: по нему интерфейс проверяется на устройстве
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            Box(Modifier.fillMaxSize()) {
                when (controller.screen) {
                    Screen.Login -> LoginScreen(controller)
                    Screen.Unlock -> UnlockScreen(controller, activity)
                    Screen.Vault -> VaultScreen(controller, activity)
                }
                // Box с фоном, а не Surface: Surface поглощает касания, а уведомление в центре
                // не должно мешать нажимать на список и кнопки под ним
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    controller.notices.forEach {
                        Box(
                            Modifier
                                .shadow(8.dp, MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    singleLine: Boolean = true,
    hint: String? = null,
    error: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        // без переноса: с несколькими иконками справа метка иначе занимает две строки,
        // лишнее обрезается у иконок так же, как введенный текст
        label = { Text(label, maxLines = 1, softWrap = false) },
        supportingText = hint?.let { { Text(it) } },
        trailingIcon = actions?.let { content -> { Row { content() } } },
        isError = error,
        singleLine = singleLine,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth().tag(tag),
    )
}

// Подпись по долгому нажатию: значок без текста должен оставаться понятным
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconAction(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick, Modifier.tag(tag)) { Icon(icon, description) }
    }
}

// Нажатие на всю строку, а не только на квадрат: больше область касания, TalkBack читает строку целиком
@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String, tag: String? = null, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .then(if (tag != null) Modifier.tag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        // прозрачность недоступного текста по Material 3, как у самого флажка
        Text(label, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@Composable
private fun Form(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun LoginScreen(controller: VaultController) {
    val saved = controller.settings
    var jid by remember { mutableStateOf(saved?.jid.orEmpty()) }
    var xmppPassword by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(saved?.host.orEmpty()) }
    var port by remember { mutableStateOf((saved?.port ?: 5222).toString()) }
    val noVault = controller.noVault
    var confirmOrphans by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    Form {
        Text("InkVault", style = MaterialTheme.typography.headlineMedium)
        Field(jid, { jid = it.trim() }, "JID", "jid", hint = stringResource(R.string.login_jid_hint))
        Field(xmppPassword, { xmppPassword = it }, stringResource(R.string.login_xmpp_password), "xmpp-password", secret = true)
        Field(master, { master = it }, stringResource(R.string.master_password), "master-password", secret = true)
        Field(host, { host = it.trim() }, stringResource(R.string.login_host), "host", hint = stringResource(R.string.login_host_hint))
        Field(port, { port = it.filter(Char::isDigit) }, stringResource(R.string.login_port), "port")
        val settings = Settings(jid, host, port.toIntOrNull() ?: 5222)
        Button({ controller.login(settings, xmppPassword, master, create = false) }, Modifier.fillMaxWidth().tag("open"), enabled = !controller.busy) {
            Text(stringResource(R.string.login_open))
        }
        if (controller.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("error")) }
        if (noVault != null) {
            if (!noVault) Text(stringResource(R.string.login_create_prompt))
            Field(repeat, { repeat = it }, stringResource(R.string.login_repeat), "repeat-password", secret = true)
            // записи без учетной записи: прежнее хранилище может восстановить устройство с кэшем, создание поверх - только явно
            if (noVault) CheckRow(confirmOrphans, { confirmOrphans = it }, stringResource(R.string.login_confirm_orphans), "confirm-orphans")
            Button(
                {
                    if (repeat == master) controller.login(settings, xmppPassword, master, create = true, replaceOrphans = confirmOrphans)
                    else controller.error = resources.getString(R.string.login_master_mismatch)
                },
                Modifier.fillMaxWidth().tag("create"),
                enabled = !controller.busy && (!noVault || confirmOrphans),
            ) { Text(stringResource(R.string.login_create)) }
        }
    }
}

@Composable
private fun UnlockScreen(controller: VaultController, activity: Activity) {
    var master by remember { mutableStateOf("") }
    Form {
        Text("InkVault", style = MaterialTheme.typography.headlineMedium)
        Text(controller.settings?.jid.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(master, { master = it }, stringResource(R.string.master_password), "master-password", secret = true)
        Button({ controller.unlock(master).also { master = "" } }, Modifier.fillMaxWidth().tag("unlock"), enabled = !controller.busy) { Text(stringResource(R.string.unlock)) }
        if (controller.biometricEnabled) {
            OutlinedButton({ controller.unlockWithBiometrics(activity) }, Modifier.fillMaxWidth().tag("unlock-biometric")) { Text(stringResource(R.string.unlock_biometric)) }
        }
        if (controller.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("error")) }
        TextButton({ controller.forget() }, Modifier.tag("forget")) { Text(stringResource(R.string.forget)) }
    }
}

private sealed interface Filter {
    data object All : Filter
    data object Favorites : Filter
    data object Trash : Filter
    data class Tag(val tag: String) : Filter
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultScreen(controller: VaultController, activity: Activity) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Filter>(Filter.All) }
    var editing by remember { mutableStateOf<String?>(null) }
    var generator by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var backup by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(false) }
    var deleteVault by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    // при конфликте выбор можно скрыть и открыть снова нажатием на статус
    var conflictHidden by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val resources = LocalResources.current
    // отсчет до повторного подключения обновляется раз в секунду, пока повтор не наступил
    LaunchedEffect(controller.retryAt) {
        now = System.currentTimeMillis()
        while (now < controller.retryAt) {
            delay(1000 - now % 1000)
            now = System.currentTimeMillis()
        }
    }

    editing?.let { id ->
        BackHandler { editing = null }
        EditorScreen(controller, if (id == NEW) null else controller.items.firstOrNull { it.id == id }) { editing = it }
        return
    }

    val tags = controller.items.filterNot { it.deleted }.flatMap { it.userTags }
        .flatMap { tag -> tag.split("/").indices.map { tag.split("/").take(it + 1).joinToString("/") } }.toSortedSet()
    val visible = controller.items.filter { item ->
        val inFilter = when (val f = filter) {
            Filter.All -> !item.deleted
            Filter.Favorites -> !item.deleted && item.favorite
            Filter.Trash -> item.deleted
            is Filter.Tag -> !item.deleted && item.userTags.any { it == f.tag || it.startsWith("${f.tag}/") }
        }
        val q = query.trim().lowercase()
        inFilter && (q.isEmpty() || (listOf("name", "username", "notes").map(item::field) + item.urls + item.userTags).any { it.lowercase().contains(q) })
    }.sortedBy { it.field("name").lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // JID вместо названия приложения: третья строка не помещается в высоту панели.
                        // Многоточие в середине оставляет видимыми и имя, и сервер
                        Text(
                            controller.settings?.jid.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                            modifier = Modifier.tag("account"),
                        )
                        // tertiary из палитры темы: заметнее статуса и сочетается с остальными цветами
                        val pendingColor = MaterialTheme.colorScheme.tertiary
                        val status = buildAnnotatedString {
                            val problem = controller.connectionProblem
                            if (controller.status == Status.OFFLINE && problem != null) {
                                append(problem)
                                // now - только повод перерисовать раз в секунду: в первом кадре после новой попытки
                                // он еще старый, поэтому время берется текущее
                                val seconds = (controller.retryAt - maxOf(now, System.currentTimeMillis()) + 999) / 1000
                                if (seconds > 0) append(stringResource(R.string.status_retry, seconds))
                            } else {
                                append(stringResource(controller.status.label))
                            }
                            if (controller.pending > 0) {
                                append(", ")
                                withStyle(SpanStyle(color = pendingColor)) { append(stringResource(R.string.status_pending, controller.pending)) }
                            }
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (controller.status == Status.CONFLICT) MaterialTheme.colorScheme.error else Color.Unspecified,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = controller.status == Status.CONFLICT) { conflictHidden = false }.tag("status"),
                        )
                    }
                },
                actions = {
                    IconAction(AppIcons.Key, stringResource(R.string.generator), "generator") { generator = true }
                    Box {
                        IconAction(AppIcons.MoreVert, stringResource(R.string.more), "menu") { menu = true }
                        DropdownMenu(menu, { menu = false }) {
                            if (Biometric.supported) {
                                if (controller.biometricEnabled) {
                                    DropdownMenuItem({ Text(stringResource(R.string.biometric_disable)) }, { menu = false; controller.disableBiometrics() }, Modifier.tag("biometric-off"))
                                } else {
                                    DropdownMenuItem({ Text(stringResource(R.string.biometric_enable)) }, { menu = false; controller.enableBiometrics(activity) }, Modifier.tag("biometric-on"))
                                }
                            }
                            DropdownMenuItem({ Text(stringResource(R.string.backup)) }, { menu = false; backup = true }, Modifier.tag("backup"))
                            DropdownMenuItem({ Text(stringResource(R.string.change_password)) }, { menu = false; changePassword = true }, Modifier.tag("change-password"))
                            DropdownMenuItem({ Text(stringResource(R.string.item_limit)) }, { menu = false; limit = true }, Modifier.tag("limit"))
                            DropdownMenuItem({ Text(stringResource(R.string.about)) }, { menu = false; about = true }, Modifier.tag("about"))
                            DropdownMenuItem({ Text(stringResource(R.string.lock)) }, { menu = false; controller.lock() }, Modifier.tag("lock"))
                            HorizontalDivider()
                            DropdownMenuItem({ Text(stringResource(R.string.delete_vault), color = MaterialTheme.colorScheme.error) }, { menu = false; deleteVault = true }, Modifier.tag("delete-vault"))
                        }
                    }
                },
            )
        },
        floatingActionButton = { FloatingActionButton({ editing = NEW }, Modifier.tag("new")) { Icon(AppIcons.Add, stringResource(R.string.add_item)) } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (controller.status == Status.CONFLICT && !conflictHidden) {
                Surface(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp).tag("account-conflict"),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.conflict_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            stringResource(R.string.conflict_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton({ conflictHidden = true }, Modifier.tag("conflict-keep")) { Text(stringResource(R.string.conflict_keep)) }
                        TextButton(
                            { confirm = resources.getString(R.string.conflict_server_confirm) to { controller.switchToServerAccount() } },
                            Modifier.tag("conflict-server"),
                        ) { Text(stringResource(R.string.conflict_server)) }
                        TextButton(
                            { confirm = resources.getString(R.string.conflict_replace_confirm) to { controller.replaceServerAccount() } },
                            Modifier.tag("conflict-replace"),
                        ) { Text(stringResource(R.string.conflict_replace), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).tag("search"),
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(AppIcons.Search, null) },
                trailingIcon = if (query.isEmpty()) null else { { IconAction(AppIcons.Close, stringResource(R.string.search_clear), "search-clear") { query = "" } } },
                singleLine = true,
                shape = CircleShape,
                // список фильтруется при вводе, кнопка поиска на клавиатуре только скрывает ее
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            val chips = listOf(stringResource(R.string.filter_all) to Filter.All, stringResource(R.string.filter_favorites) to Filter.Favorites) +
                tags.map { it.substringAfterLast('/') to Filter.Tag(it) } + listOf(stringResource(R.string.filter_trash) to Filter.Trash)
            LazyRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(chips) { (label, f) ->
                    // метка для тестов не зависит от языка интерфейса
                    val key = when (f) {
                        Filter.All -> "all"
                        Filter.Favorites -> "favorites"
                        Filter.Trash -> "trash"
                        is Filter.Tag -> f.tag
                    }
                    FilterChip(filter == f, { filter = f }, { Text(label) }, Modifier.tag("filter-$key"))
                }
            }
            val untitled = stringResource(R.string.untitled)
            val unsent = stringResource(R.string.item_unsent)
            // отступ снизу под FAB: иначе он закрывает последнюю запись
            LazyColumn(Modifier.fillMaxSize().tag("items"), contentPadding = PaddingValues(bottom = 88.dp)) {
                val trash = controller.items.count { it.deleted }
                if (filter == Filter.Trash && trash > 0) item {
                    OutlinedButton(
                        { confirm = resources.getString(R.string.purge_trash_confirm, trash) to { controller.purgeTrash() } },
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).tag("purge-trash"),
                    ) { Text(stringResource(R.string.purge_trash), color = MaterialTheme.colorScheme.error) }
                }
                items(visible, key = { it.id }) { item ->
                    // url бывает пустым при заполненном urls, если запись правил другой клиент
                    val details = listOf(item.field("username"), item.urls.firstOrNull().orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
                    ListItem(
                        headlineContent = { Text(item.field("name").ifEmpty { untitled }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clickable { editing = item.id }.tag("item"),
                        supportingContent = if (details.isEmpty()) null else { { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        leadingContent = { Monogram(item.field("name")) },
                        trailingContent = if (!item.favorite && !item.pending) null else { {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item.pending) {
                                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape).semantics { contentDescription = unsent })
                                }
                                if (item.favorite) Icon(AppIcons.Star, stringResource(R.string.favorite), tint = MaterialTheme.colorScheme.primary)
                            }
                        } },
                    )
                }
                if (visible.isEmpty()) item {
                    val (title, hint) = when {
                        query.isNotBlank() -> R.string.empty_not_found to R.string.empty_not_found_hint
                        filter == Filter.Trash -> R.string.empty_trash to null
                        filter == Filter.All -> R.string.empty_items to R.string.empty_items_hint
                        else -> R.string.empty_items to null
                    }
                    Column(
                        Modifier.fillParentMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }

    if (generator) GeneratorDialog(onUse = null) { generator = false }
    if (backup) BackupDialog(controller) { backup = false }
    if (limit) MaxItemsDialog(controller) { limit = false }
    if (deleteVault) DeleteVaultDialog(controller) { deleteVault = false }
    if (changePassword) ChangePasswordDialog(controller) { changePassword = false }
    if (about) AboutDialog { about = false }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton({ confirm = null; action() }, Modifier.tag("confirm-ok")) { Text(stringResource(R.string.confirm_continue), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirm = null }, Modifier.tag("confirm-cancel")) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// Буква названия вместо значка сайта: загрузка значков раскрыла бы адреса записей стороннему серверу
@Composable
private fun Monogram(name: String) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    fun line(label: String, text: String, uri: String) = buildAnnotatedString {
        append("$label: ")
        // без приложения для mailto: или xmpp: openUri бросает исключение
        withLink(LinkAnnotation.Url(uri, linkStyle) { runCatching { uriHandler.openUri(uri) } }) { append(text) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("InkVault") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.about_author, "Evgeny Inkov"))
                Text(line(stringResource(R.string.about_email), "me@inkov.dev", "mailto:me@inkov.dev"))
                Text(line("XMPP", "tzx1z@inkov.dev", "xmpp:tzx1z@inkov.dev"))
                Text(line(stringResource(R.string.about_site), "inkov.dev", "https://inkov.dev"))
                Text(line(stringResource(R.string.about_license), "GNU AGPL v3.0", "https://www.gnu.org/licenses/agpl-3.0.html"))
            }
        },
        confirmButton = { TextButton(onClose, Modifier.tag("about-close")) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun MaxItemsDialog(controller: VaultController, onClose: () -> Unit) {
    val current = remember { controller.maxItems }
    var value by remember { mutableStateOf(current.toString()) }
    val problem = controller.maxItemsProblem(value.toIntOrNull())
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.item_limit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.limit_text), style = MaterialTheme.typography.bodySmall)
                Field(value, { value = it.filter(Char::isDigit).take(6) }, stringResource(R.string.item_limit), "mi-value", hint = problem ?: stringResource(R.string.limit_hint), error = problem != null)
            }
        },
        confirmButton = {
            TextButton(
                { onClose(); controller.changeMaxItems(value.toInt()) },
                Modifier.tag("mi-save"),
                enabled = problem == null && value.toIntOrNull() != current,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClose, Modifier.tag("mi-cancel")) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChangePasswordDialog(controller: VaultController, onClose: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.change_password_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.change_password_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field(current, { current = it }, stringResource(R.string.current_master_password), "cp-current", secret = true)
                Field(newPassword, { newPassword = it }, stringResource(R.string.new_master_password), "cp-new", secret = true)
                Field(repeat, { repeat = it }, stringResource(R.string.new_master_password_repeat), "cp-repeat", secret = true)
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("cp-error")) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    error = null
                    if (newPassword != repeat) {
                        error = resources.getString(R.string.new_passwords_mismatch)
                        return@TextButton
                    }
                    busy = true
                    controller.changePassword(current, newPassword, onError = { error = it; busy = false }) { onClose() }
                },
                Modifier.tag("cp-save"),
                enabled = !busy && current.isNotEmpty() && newPassword.isNotEmpty(),
            ) { Text(stringResource(R.string.change)) }
        },
        dismissButton = { TextButton(onClose, Modifier.tag("cp-cancel")) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteVaultDialog(controller: VaultController, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.delete_vault)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.delete_vault_text))
                Text(
                    stringResource(R.string.delete_vault_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field(password, { password = it }, stringResource(R.string.master_password), "dv-password", secret = true)
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("dv-error")) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    error = null
                    busy = true
                    controller.deleteVault(password, onError = { error = it; busy = false })
                },
                Modifier.tag("dv-delete"),
                enabled = !busy && password.isNotEmpty(),
            ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClose, Modifier.tag("dv-cancel")) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupDialog(controller: VaultController, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var master by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    // уведомления приложения закрыты диалогом, поэтому результат копирования показан под полем
    var copied by remember { mutableStateOf<String?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // зашифрованный файл ждет, пока пользователь выберет место сохранения
    var prepared by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val file = prepared
        prepared = null
        if (uri != null && file != null) {
            controller.writeExport(uri, file)
            onClose()
        }
    }
    // часть файловых менеджеров отдает .json как application/octet-stream или text/plain
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importUri = uri }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.backup)) },
        confirmButton = { TextButton(onClose, Modifier.tag("b-close")) { Text(stringResource(R.string.close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.export_text), style = MaterialTheme.typography.bodySmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to stringResource(R.string.export_separate), true to stringResource(R.string.master_password)).forEachIndexed { i, (isMaster, label) ->
                        SegmentedButton(
                            selected = master == isMaster,
                            onClick = { master = isMaster; error = null },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            modifier = Modifier.tag(if (isMaster) "b-mode-master" else "b-mode-separate"),
                            // без галочки у выбранного сегмента: ширина подписи не зависит от выбора, размер шрифта не скачет
                            icon = {},
                        ) {
                            // подпись в одну строку: если не помещается, уменьшается шрифт, а не переносится текст
                            Text(
                                label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        }
                    }
                }
                Text(
                    stringResource(if (master) R.string.export_master_hint else R.string.export_separate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field(
                    password, { password = it; copied = null }, stringResource(if (master) R.string.master_password else R.string.file_password), "b-password",
                    secret = !show, hint = copied.takeIf { !master },
                ) {
                    IconAction(if (show) AppIcons.VisibilityOff else AppIcons.Visibility, stringResource(if (show) R.string.hide_password else R.string.show_password), "b-show") { show = !show }
                    if (!master) IconAction(AppIcons.Copy, stringResource(R.string.copy_file_password), "b-copy") {
                        copied = copySecret(context, resources.getString(R.string.clip_file_password), password)
                    }
                    // без похожих символов и спецсимволов: пароль файла чаще переписывают на бумагу, чем вставляют
                    if (!master) IconAction(AppIcons.Generate, stringResource(R.string.generate_file_password), "b-generate") {
                        generatePassword(24u, true, true, true, false, true).also { password = it; repeat = it }
                        show = true
                        copied = null
                    }
                }
                if (!master) Field(repeat, { repeat = it }, stringResource(R.string.password_repeat), "b-repeat", secret = !show)
                Button(
                    {
                        error = null
                        if (!master && password != repeat) {
                            error = resources.getString(R.string.passwords_mismatch)
                            return@Button
                        }
                        busy = true
                        controller.prepareExport(password, master, onError = { error = it; busy = false }) { file ->
                            busy = false
                            prepared = file
                            save.launch("inkvault-export-${LocalDate.now()}.json")
                        }
                    },
                    Modifier.tag("b-export"),
                    enabled = !busy && password.isNotEmpty(),
                ) { Text(stringResource(R.string.save_file)) }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.import_text), style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, Modifier.fillMaxWidth().tag("b-file")) {
                    Text(importUri?.let { displayName(context, it) } ?: stringResource(R.string.choose_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Field(importPassword, { importPassword = it }, stringResource(R.string.file_password), "b-import-password", secret = true)
                Button(
                    {
                        val uri = importUri ?: return@Button
                        error = null
                        busy = true
                        controller.importFrom(uri, importPassword, onError = { error = it; busy = false }) { onClose() }
                    },
                    Modifier.tag("b-import"),
                    enabled = !busy && importUri != null && importPassword.isNotEmpty(),
                ) { Text(stringResource(R.string.import_action)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("b-error")) }
            }
        },
    )
}

private const val NEW = "new"

// combined - режим "Пароль + код TOTP" в форме. password_mode в fields не входит: он пишется только при смене режима
private data class Draft(val fields: Map<String, String>, val tags: List<String>, val combined: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(controller: VaultController, item: Item?, navigate: (String?) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val values = remember(item?.id) { mutableStateOf(dev.inkov.inkvault.android.FIELDS.associateWith { item?.field(it).orEmpty() }) }
    // адреса правятся списком, при сохранении первый уходит в url, остальные - в urls
    var urls by remember(item?.id) { mutableStateOf(item?.urls.orEmpty().ifEmpty { listOf("") }) }
    var tags by remember(item?.id) { mutableStateOf(item?.userTags?.joinToString(", ").orEmpty()) }
    var favorite by remember(item?.id) { mutableStateOf(item?.favorite ?: false) }
    var combined by remember(item?.id) { mutableStateOf(item?.field(PASSWORD_MODE) == PIN_TOTP) }
    var showPassword by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }
    var generator by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val lastUrl = remember { FocusRequester() }
    var focusLastUrl by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusLastUrl) { if (focusLastUrl > 0) lastUrl.requestFocus() }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    // разрешение запрашивается при первом сканировании, а не при запуске
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else controller.notice(R.string.camera_denied)
    }
    fun set(name: String, value: String) {
        values.value = values.value + (name to value)
    }
    val v = values.value
    val deleted = item?.deleted ?: false
    val secret = v["totp"].orEmpty().trim()
    val secretError = if (secret.isEmpty()) null else runCatching { totp(secret, 0u) }.exceptionOrNull()?.message
    val secretValid = secret.isNotEmpty() && secretError == null
    // без рабочего секрета режим выключен, но выбор остается: он вернется, когда секрет исправят
    val combinedOn = combined && secretValid
    // значения формы в том виде, в каком их сохраняет Save
    fun collect(): Draft {
        // вставка в однострочное поле может принести перевод строки, а он разделяет адреса в urls
        val urlList = urls.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val fields = v + mapOf("url" to urlList.firstOrNull().orEmpty(), "urls" to urlList.drop(1).joinToString("\n"), "totp" to secret)
        val tagList = tags.split(",").map { it.trim().trim('/') }.filter { it.isNotEmpty() } + if (favorite) listOf(FAVORITE) else emptyList()
        return Draft(fields, tagList, combinedOn)
    }
    // у существующей записи id после сохранения тот же и remember не сбрасывается, поэтому Save обновляет saved сам
    var saved by remember(item?.id) { mutableStateOf(collect()) }
    // запись в корзине сохранить нельзя, уведомления у нее нет
    val unsaved = !deleted && collect() != saved

    Scaffold(topBar = {
        TopAppBar(
            title = {
                val name = v["name"].orEmpty().ifEmpty { stringResource(R.string.untitled) }
                Column {
                    Text(
                        when {
                            item == null -> stringResource(R.string.new_item)
                            deleted -> stringResource(R.string.in_trash, name)
                            else -> name
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (unsaved) {
                        Text(
                            stringResource(R.string.unsaved_changes),
                            Modifier.tag("unsaved").semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            navigationIcon = { IconAction(AppIcons.ArrowBack, stringResource(R.string.back), "back") { navigate(null) } },
        )
    }) { padding ->
        // padding уже содержит навигационную панель, без consumeWindowInsets imePadding прибавил бы ее второй раз
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field(v["name"].orEmpty(), { set("name", it) }, stringResource(R.string.field_name), "f-name")
            urls.forEachIndexed { i, url ->
                Field(
                    url, { value -> urls = urls.toMutableList().also { it[i] = value } }, stringResource(R.string.field_url), if (i == 0) "f-url" else "f-url-$i",
                    modifier = if (i == urls.lastIndex) Modifier.focusRequester(lastUrl) else Modifier,
                ) {
                    if (i == 0) {
                        // пустое поле в конце уже есть - курсор переходит в него, второе пустое не добавляется
                        IconAction(AppIcons.Add, stringResource(R.string.add_url), "add-url") {
                            if (urls.last().isNotBlank()) urls = urls + ""
                            focusLastUrl++
                        }
                    } else {
                        IconAction(AppIcons.Close, stringResource(R.string.remove_url), "remove-url-$i") { urls = urls.filterIndexed { j, _ -> j != i } }
                    }
                }
            }
            Field(v["username"].orEmpty(), { set("username", it) }, stringResource(R.string.field_username), "f-username") {
                IconAction(AppIcons.Copy, stringResource(R.string.copy_username), "copy-username") {
                    controller.notice(copySecret(context, resources.getString(R.string.clip_username), v["username"].orEmpty()))
                }
            }
            Field(
                v["password"].orEmpty(), { set("password", it) }, stringResource(R.string.field_password), "f-password",
                secret = !showPassword, hint = if (combinedOn) stringResource(R.string.combined_password_hint) else null,
            ) {
                IconAction(
                    if (showPassword) AppIcons.VisibilityOff else AppIcons.Visibility,
                    stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                    "show-password",
                ) { showPassword = !showPassword }
                IconAction(AppIcons.Copy, stringResource(R.string.copy_password), "copy-password") {
                    controller.notice(copySecret(context, resources.getString(R.string.clip_password), v["password"].orEmpty()))
                }
                IconAction(AppIcons.Key, stringResource(R.string.generate_password), "gen-password") { generator = true }
            }
            Field(
                v["totp"].orEmpty(), { set("totp", it) }, stringResource(R.string.totp_secret), "f-totp",
                secret = !showSecret, hint = secretError ?: stringResource(R.string.totp_hint), error = secretError != null,
            ) {
                IconAction(if (showSecret) AppIcons.VisibilityOff else AppIcons.Visibility, stringResource(if (showSecret) R.string.hide_secret else R.string.show_secret), "show-totp") { showSecret = !showSecret }
                if (hasCamera) {
                    IconAction(AppIcons.QrCodeScanner, stringResource(R.string.scan_qr), "scan-totp") {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }
            if (secretValid) {
                TotpFields(controller, v["password"].orEmpty(), secret, combinedOn) { combined = it }
            } else {
                CheckRow(false, {}, stringResource(R.string.password_plus_totp), "f-combined", enabled = false)
            }
            Field(v["notes"].orEmpty(), { set("notes", it) }, stringResource(R.string.field_notes), "f-notes", singleLine = false)
            Field(tags, { tags = it }, stringResource(R.string.field_tags), "f-tags", hint = stringResource(R.string.tags_hint))
            CheckRow(favorite, { favorite = it }, stringResource(R.string.favorite), "f-favorite")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!deleted) {
                    Button({
                        if (secretError != null) {
                            controller.notice(R.string.not_saved, secretError)
                            return@Button
                        }
                        val draft = collect()
                        var fields = draft.fields
                        // режим пишется только при смене: незнакомое значение от более нового клиента не затирается
                        if (draft.combined != (item?.field(PASSWORD_MODE) == PIN_TOTP)) fields = fields + (PASSWORD_MODE to if (draft.combined) PIN_TOTP else "")
                        if (item == null) {
                            controller.create(fields.filterValues { it.isNotEmpty() }, draft.tags) { id -> navigate(id) }
                        } else {
                            controller.update(item.id, fields, draft.tags)
                        }
                        saved = draft
                        controller.notice(if (item == null) R.string.item_created else R.string.item_saved)
                    }, Modifier.tag("save")) { Text(stringResource(R.string.save)) }
                }
                if (item != null && !deleted) OutlinedButton({ controller.delete(item.id); navigate(null) }, Modifier.tag("delete")) { Text(stringResource(R.string.move_to_trash)) }
                if (item != null && deleted) Button({ controller.restore(item.id); navigate(null) }, Modifier.tag("restore")) { Text(stringResource(R.string.restore)) }
            }
            // одно значение может встретиться в истории несколько раз (docs/protocol.md в inkvault-core), повторы скрыты
            val history = item?.history?.distinct()?.filter { it != item.field("password") }.orEmpty()
            if (history.isNotEmpty()) {
                Text(stringResource(R.string.password_history, history.size), style = MaterialTheme.typography.titleSmall)
                history.forEach { old ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("•".repeat(minOf(old.length, 16)), Modifier.weight(1f))
                        TextButton({ controller.notice(copySecret(context, resources.getString(R.string.clip_old_password), old)) }) { Text(stringResource(R.string.copy)) }
                    }
                }
            }
        }
    }
    if (generator) GeneratorDialog(onUse = { set("password", it) }) { generator = false }
    if (scanning) QrScanDialog(onResult = { set("totp", it); scanning = false }) { scanning = false }
}

// Код и готовый пароль не хранятся: код TOTP меняется каждые period секунд, поэтому считается при показе и копировании
@Composable
private fun TotpFields(controller: VaultController, password: String, secret: String, combined: Boolean, onCombinedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    val code = runCatching { totp(secret, now.toULong()) }.getOrNull() ?: return
    // при копировании код берется на момент нажатия: показанный мог смениться до следующего тика
    fun current() = totp(secret, System.currentTimeMillis().toULong()).code
    val monospace = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
    OutlinedTextField(
        value = code.code,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.totp_code)) },
        leadingIcon = {
            CircularProgressIndicator(progress = { code.remaining.toFloat() / code.period.toFloat() }, Modifier.size(22.dp), strokeWidth = 3.dp)
        },
        trailingIcon = {
            IconAction(AppIcons.Copy, stringResource(R.string.copy_totp_code), "totp-code-copy") {
                controller.notice(copySecret(context, resources.getString(R.string.clip_totp_code), current()))
            }
        },
        supportingText = { Text(stringResource(R.string.otp_remaining, code.remaining.toInt()), Modifier.tag("totp-remaining")) },
        singleLine = true,
        textStyle = monospace,
        modifier = Modifier.fillMaxWidth().tag("totp-code"),
    )
    CheckRow(combined, onCombinedChange, stringResource(R.string.password_plus_totp), "f-combined")
    if (!combined) return
    OutlinedTextField(
        value = password + code.code,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.password_plus_totp)) },
        trailingIcon = {
            Row {
                IconAction(if (shown) AppIcons.VisibilityOff else AppIcons.Visibility, stringResource(if (shown) R.string.hide_otp else R.string.show_otp), "combined-show") { shown = !shown }
                IconAction(AppIcons.Copy, stringResource(R.string.copy_otp), "combined-copy") {
                    controller.notice(copySecret(context, resources.getString(R.string.clip_password), password + current()))
                }
            }
        },
        singleLine = true,
        // моноширинный шрифт только для открытого пароля: скрытые точки выглядят как в остальных полях
        textStyle = if (shown) monospace else LocalTextStyle.current,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().tag("combined-value"),
    )
}

/**
 * Сканирование QR-кода с секретом TOTP. Кадры распознаются на устройстве и не сохраняются,
 * в onResult попадает только текст, который ядро принимает как секрет.
 */
@Composable
private fun QrScanDialog(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val found = Channel<String>(Channel.CONFLATED)
        val executor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply { setSurfaceProvider { request = it } }
        // по умолчанию анализ получает 640x480: мелкий QR-код на экране ноутбука распознается хуже
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
            .apply { setAnalyzer(executor, QrAnalyzer { found.trySend(it) }) }
        var provider: ProcessCameraProvider? = null
        try {
            provider = ProcessCameraProvider.awaitInstance(context)
            val camera = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            provider.bindToLifecycle(lifecycleOwner, camera, preview, analysis)
            // один и тот же код приходит с каждым кадром, проверяется только новый текст
            var last: String? = null
            for (text in found) {
                if (text == last) continue
                last = text
                val error = scannedSecretProblem(resources, text)
                problem = error
                if (error == null) {
                    result(text.trim())
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = resources.getString(R.string.camera_error, e.message ?: e.javaClass.simpleName)
        } finally {
            provider?.unbind(preview, analysis)
            executor.shutdown()
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.scan_qr)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Color.Black)) {
                    // TextureView, а не SurfaceView: кадр обрезается по скругленной рамке и остается в окне диалога под FLAG_SECURE
                    request?.let { CameraXViewfinder(it, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED) }
                }
                Text(
                    problem ?: stringResource(R.string.scan_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (problem != null) Modifier.tag("scan-error") else Modifier,
                )
            }
        },
        confirmButton = { TextButton(onClose, Modifier.tag("scan-cancel")) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun strength(bits: Double) = when {
    bits < 60 -> R.string.strength_weak
    bits < 80 -> R.string.strength_medium
    bits < 100 -> R.string.strength_strong
    else -> R.string.strength_very_strong
}

@Composable
private fun GeneratorDialog(onUse: ((String) -> Unit)?, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var length by remember { mutableStateOf(20f) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var ambiguous by remember { mutableStateOf(false) }
    var counter by remember { mutableStateOf(0) }
    val result = remember(length.toInt(), lower, upper, digits, symbols, ambiguous, counter) {
        runCatching {
            val n = length.toInt().toUInt()
            generatePassword(n, lower, upper, digits, symbols, ambiguous) to passwordEntropy(n, lower, upper, digits, symbols, ambiguous)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.generator)) },
        text = {
            Column(Modifier.widthIn(max = 480.dp)) {
                result.onSuccess { (password, bits) ->
                    Text(password, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.tag("g-value"))
                    Text(pluralStringResource(R.plurals.generator_entropy, bits.toInt(), bits.toInt(), stringResource(strength(bits))), style = MaterialTheme.typography.bodySmall)
                }.onFailure { Text(it.message.orEmpty(), color = MaterialTheme.colorScheme.error) }
                Text(stringResource(R.string.generator_length, length.toInt()))
                Slider(length, { length = it }, valueRange = 8f..64f, modifier = Modifier.tag("g-length"))
                listOf(
                    Triple(stringResource(R.string.generator_lower), lower) { b: Boolean -> lower = b },
                    Triple(stringResource(R.string.generator_upper), upper) { b: Boolean -> upper = b },
                    Triple(stringResource(R.string.generator_digits), digits) { b: Boolean -> digits = b },
                    Triple(stringResource(R.string.generator_symbols), symbols) { b: Boolean -> symbols = b },
                    Triple(stringResource(R.string.generator_ambiguous), ambiguous) { b: Boolean -> ambiguous = b },
                ).forEach { (label, checked, change) -> CheckRow(checked, change, label) }
                Row {
                    TextButton({ counter++ }) { Text(stringResource(R.string.generator_again)) }
                    TextButton({ result.onSuccess { copySecret(context, resources.getString(R.string.clip_password), it.first) } }) { Text(stringResource(R.string.copy)) }
                }
            }
        },
        confirmButton = {
            if (onUse != null) {
                TextButton({ result.onSuccess { onUse(it.first) }; onClose() }, Modifier.tag("g-use")) { Text(stringResource(R.string.generator_use)) }
            }
        },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.close)) } },
    )
}
