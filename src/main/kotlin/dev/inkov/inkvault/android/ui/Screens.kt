package dev.inkov.inkvault.android.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.inkov.inkvault.android.Biometric
import dev.inkov.inkvault.android.FAVORITE
import dev.inkov.inkvault.android.Item
import dev.inkov.inkvault.android.PASSWORD_MODE
import dev.inkov.inkvault.android.PIN_TOTP
import dev.inkov.inkvault.android.Screen
import dev.inkov.inkvault.android.Settings
import dev.inkov.inkvault.android.Status
import dev.inkov.inkvault.android.VaultController
import dev.inkov.inkvault.android.copySecret
import uniffi.inkvault_uniffi.generatePassword
import java.time.LocalDate
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
    secret: Boolean = false,
    singleLine: Boolean = true,
    hint: String? = null,
    error: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        trailingIcon = actions?.let { content -> { Row { content() } } },
        isError = error,
        singleLine = singleLine,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().tag(tag),
    )
}

// Кнопка-значок внутри поля. Подпись по долгому нажатию: значок без текста должен оставаться понятным
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldAction(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick, Modifier.tag(tag)) { Icon(icon, description) }
    }
}

@Composable
private fun Form(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
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
    var insecure by remember { mutableStateOf(saved?.insecure ?: false) }
    val noVault = controller.noVault
    var confirmOrphans by remember { mutableStateOf(false) }
    Form {
        Text("InkVault", style = MaterialTheme.typography.headlineMedium)
        Field(jid, { jid = it.trim() }, "JID", "jid", hint = "имя@сервер")
        Field(xmppPassword, { xmppPassword = it }, "Пароль XMPP", "xmpp-password", secret = true)
        Field(master, { master = it }, "Мастер-пароль", "master-password", secret = true)
        Field(host, { host = it.trim() }, "Адрес сервера", "host", hint = "Пусто - по SRV-записям домена")
        Field(port, { port = it.filter(Char::isDigit) }, "Порт", "port")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(insecure, { insecure = it }, Modifier.tag("insecure"))
            Text("Без TLS (только тестовый сервер)")
        }
        val settings = Settings(jid, host, port.toIntOrNull() ?: 5222, insecure)
        Button({ controller.login(settings, xmppPassword, master, create = false) }, Modifier.fillMaxWidth().tag("open"), enabled = !controller.busy) {
            Text("Открыть хранилище")
        }
        if (controller.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("error")) }
        if (noVault != null) {
            if (!noVault) Text("Создать новое хранилище? Мастер-пароль восстановить будет нельзя, повторите его.")
            Field(repeat, { repeat = it }, "Мастер-пароль еще раз", "repeat-password", secret = true)
            // записи без учетной записи: прежнее хранилище может восстановить устройство с кэшем, создание поверх - только явно
            if (noVault) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(confirmOrphans, { confirmOrphans = it }, Modifier.tag("confirm-orphans"))
                    Text("Все равно создать новое хранилище. Записи на сервере будут удалены")
                }
            }
            Button(
                {
                    if (repeat == master) controller.login(settings, xmppPassword, master, create = true, replaceOrphans = confirmOrphans)
                    else controller.error = "мастер-пароли не совпадают"
                },
                Modifier.fillMaxWidth().tag("create"),
                enabled = !controller.busy && (!noVault || confirmOrphans),
            ) { Text("Создать хранилище") }
        }
    }
}

@Composable
private fun UnlockScreen(controller: VaultController, activity: Activity) {
    var master by remember { mutableStateOf("") }
    Form {
        Text("InkVault", style = MaterialTheme.typography.headlineMedium)
        Text(controller.settings?.jid.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(master, { master = it }, "Мастер-пароль", "master-password", secret = true)
        Button({ controller.unlock(master).also { master = "" } }, Modifier.fillMaxWidth().tag("unlock"), enabled = !controller.busy) { Text("Разблокировать") }
        if (controller.biometricEnabled) {
            OutlinedButton({ controller.unlockWithBiometrics(activity) }, Modifier.fillMaxWidth().tag("unlock-biometric")) { Text("Разблокировать отпечатком") }
        }
        if (controller.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("error")) }
        TextButton({ controller.forget() }, Modifier.tag("forget")) { Text("Выйти с этого устройства") }
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
        inFilter && (q.isEmpty() || (listOf("name", "username", "url", "notes").map(item::field) + item.userTags).any { it.lowercase().contains(q) })
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
                                // неразрывные пробелы: при переносе "через 9 с" остается одной строкой
                                if (seconds > 0) append(", повтор через\u00A0$seconds\u00A0с")
                            } else {
                                append(controller.status.label)
                            }
                            if (controller.pending > 0) {
                                append(", ")
                                withStyle(SpanStyle(color = pendingColor)) { append("не отправлено: ${controller.pending}") }
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
                    TextButton({ generator = true }, Modifier.tag("generator")) { Text("Генератор") }
                    TextButton({ menu = true }, Modifier.tag("menu")) { Text("Еще") }
                },
            )
        },
        floatingActionButton = { FloatingActionButton({ editing = NEW }, Modifier.tag("new")) { Text("+", style = MaterialTheme.typography.headlineSmall) } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (controller.status == Status.CONFLICT && !conflictHidden) {
                Surface(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp).tag("account-conflict"),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("На сервере другая учетная запись хранилища", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Мастер-пароль этого устройства ее не открывает. Синхронизация остановлена, записи на этом устройстве доступны и сохраняются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton({ conflictHidden = true }, Modifier.tag("conflict-keep")) { Text("Работать на этом устройстве") }
                        TextButton(
                            {
                                confirm = "Учетная запись этого устройства будет закрыта, дальше нужен мастер-пароль учетной записи с сервера. " +
                                    "Записи этого устройства, которых нет на сервере, станут недоступны, разблокировка отпечатком выключится." to { controller.switchToServerAccount() }
                            },
                            Modifier.tag("conflict-server"),
                        ) { Text("Войти в учетную запись с сервера") }
                        TextButton(
                            {
                                confirm = "Учетная запись на сервере будет заменена учетной записью этого устройства, записи на сервере удалены, а записи этого устройства опубликованы заново. " +
                                    "Устройства с учетной записью с сервера потеряют доступ к ее записям, если у них нет своей копии." to { controller.replaceServerAccount() }
                            },
                            Modifier.tag("conflict-replace"),
                        ) { Text("Заменить учетную запись на сервере", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).tag("search"), placeholder = { Text("Поиск") }, singleLine = true)
            LazyRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val chips = listOf("Все" to Filter.All, "Избранное" to Filter.Favorites) + tags.map { it.substringAfterLast('/') to Filter.Tag(it) } + listOf("Корзина" to Filter.Trash)
                items(chips) { (label, f) -> FilterChip(filter == f, { filter = f }, { Text(label) }, Modifier.tag("filter-$label")) }
            }
            LazyColumn(Modifier.fillMaxSize().tag("items")) {
                val trash = controller.items.count { it.deleted }
                if (filter == Filter.Trash && trash > 0) item {
                    OutlinedButton(
                        { confirm = "Записи в корзине ($trash) будут удалены навсегда на всех устройствах. Восстановить их будет нельзя." to { controller.purgeTrash() } },
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).tag("purge-trash"),
                    ) { Text("Очистить корзину", color = MaterialTheme.colorScheme.error) }
                }
                items(visible, key = { it.id }) { item ->
                    Column(Modifier.fillMaxWidth().clickable { editing = item.id }.padding(horizontal = 16.dp, vertical = 10.dp).tag("item")) {
                        val pendingColor = MaterialTheme.colorScheme.tertiary
                        Text(buildAnnotatedString {
                            append((if (item.favorite) "★ " else "") + item.field("name").ifEmpty { "(без названия)" })
                            if (item.pending) withStyle(SpanStyle(color = pendingColor)) { append(" ●") }
                        })
                        Text(listOf(item.field("username"), item.field("url")).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider()
                }
                if (visible.isEmpty()) item { Text(if (filter == Filter.Trash) "Корзина пуста" else "Записей нет", Modifier.padding(16.dp)) }
            }
        }
    }

    if (generator) GeneratorDialog(onUse = null) { generator = false }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            confirmButton = { TextButton({ menu = false }) { Text("Закрыть") } },
            text = {
                Column {
                    if (Biometric.supported) {
                        if (controller.biometricEnabled) {
                            TextButton({ menu = false; controller.disableBiometrics() }, Modifier.tag("biometric-off")) { Text("Выключить разблокировку отпечатком") }
                        } else {
                            TextButton({ menu = false; controller.enableBiometrics(activity) }, Modifier.tag("biometric-on")) { Text("Включить разблокировку отпечатком") }
                        }
                    }
                    TextButton({ menu = false; backup = true }, Modifier.tag("backup")) { Text("Резервная копия") }
                    TextButton({ menu = false; changePassword = true }, Modifier.tag("change-password")) { Text("Сменить мастер-пароль") }
                    TextButton({ menu = false; limit = true }, Modifier.tag("limit")) { Text("Лимит записей") }
                    TextButton({ menu = false; about = true }, Modifier.tag("about")) { Text("О программе") }
                    TextButton({ menu = false; controller.lock() }, Modifier.tag("lock")) { Text("Заблокировать") }
                    TextButton({ menu = false; deleteVault = true }, Modifier.tag("delete-vault")) { Text("Удалить хранилище", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
    if (backup) BackupDialog(controller) { backup = false }
    if (limit) MaxItemsDialog(controller) { limit = false }
    if (deleteVault) DeleteVaultDialog(controller) { deleteVault = false }
    if (changePassword) ChangePasswordDialog(controller) { changePassword = false }
    if (about) AboutDialog { about = false }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton({ confirm = null; action() }, Modifier.tag("confirm-ok")) { Text("Продолжить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirm = null }, Modifier.tag("confirm-cancel")) { Text("Отмена") } },
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
                Text("Автор: Evgeny Inkov")
                Text(line("Почта", "me@inkov.dev", "mailto:me@inkov.dev"))
                Text(line("XMPP", "tzx1z@inkov.dev", "xmpp:tzx1z@inkov.dev"))
                Text(line("Сайт", "inkov.dev", "https://inkov.dev"))
            }
        },
        confirmButton = { TextButton(onClose, Modifier.tag("about-close")) { Text("Закрыть") } },
    )
}

private const val MAX_ITEMS_HINT = "Не больше pep_max_items на сервере, в Prosody по умолчанию 256"

@Composable
private fun MaxItemsDialog(controller: VaultController, onClose: () -> Unit) {
    val current = remember { controller.maxItems }
    var value by remember { mutableStateOf(current.toString()) }
    val problem = controller.maxItemsProblem(value.toIntOrNull())
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Лимит записей") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Лимит записей узла хранилища на сервере, корзина входит в него. Больше 256 записей сервер примет, если администратор поднял pep_max_items в Prosody. Новое значение применяется сразу и действует на всех устройствах, нужна связь с сервером.", style = MaterialTheme.typography.bodySmall)
                Field(value, { value = it.filter(Char::isDigit).take(6) }, "Лимит записей", "mi-value", hint = problem ?: MAX_ITEMS_HINT, error = problem != null)
            }
        },
        confirmButton = {
            TextButton(
                { onClose(); controller.changeMaxItems(value.toInt()) },
                Modifier.tag("mi-save"),
                enabled = problem == null && value.toIntOrNull() != current,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClose, Modifier.tag("mi-cancel")) { Text("Отмена") } },
    )
}

@Composable
private fun ChangePasswordDialog(controller: VaultController, onClose: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Смена мастер-пароля") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Записи не перешифровываются. Другие устройства примут новый пароль после подключения к серверу. Файлы экспорта под мастер-паролем по-прежнему открываются старым паролем.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field(current, { current = it }, "Текущий мастер-пароль", "cp-current", secret = true)
                Field(newPassword, { newPassword = it }, "Новый мастер-пароль", "cp-new", secret = true)
                Field(repeat, { repeat = it }, "Новый мастер-пароль еще раз", "cp-repeat", secret = true)
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("cp-error")) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    error = null
                    if (newPassword != repeat) {
                        error = "новые пароли не совпадают"
                        return@TextButton
                    }
                    busy = true
                    controller.changePassword(current, newPassword, onError = { error = it; busy = false }) { onClose() }
                },
                Modifier.tag("cp-save"),
                enabled = !busy && current.isNotEmpty() && newPassword.isNotEmpty(),
            ) { Text("Сменить") }
        },
        dismissButton = { TextButton(onClose, Modifier.tag("cp-cancel")) { Text("Отмена") } },
    )
}

@Composable
private fun DeleteVaultDialog(controller: VaultController, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Удалить хранилище") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("С сервера будут удалены все записи, корзина и учетная запись хранилища, с этого устройства - учетная запись, кэш и разблокировка отпечатком. Восстановить их можно только из файла экспорта.")
                Text(
                    "Устройство, где хранилище уже открыто, при подключении вернет его на сервер. Перед удалением выйдите из хранилища на других устройствах.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field(password, { password = it }, "Мастер-пароль", "dv-password", secret = true)
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
            ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClose, Modifier.tag("dv-cancel")) { Text("Отмена") } },
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
    var master by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
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
        title = { Text("Резервная копия") },
        confirmButton = { TextButton(onClose, Modifier.tag("b-close")) { Text("Закрыть") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Экспорт в файл", style = MaterialTheme.typography.titleSmall)
                Text("Все записи хранилища, включая корзину и историю паролей, в зашифрованном файле.", style = MaterialTheme.typography.bodySmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Свой пароль", true to "Мастер-пароль").forEachIndexed { i, (isMaster, label) ->
                        SegmentedButton(
                            selected = master == isMaster,
                            onClick = { master = isMaster; error = null },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            modifier = Modifier.tag(if (isMaster) "b-mode-master" else "b-mode-separate"),
                        ) { Text(label) }
                    }
                }
                Text(
                    if (master) "Защита от потери сервера, но не от потери мастер-пароля"
                    else "Файл откроется и при потере мастер-пароля, если пароль файла хранится отдельно от него",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field(password, { password = it }, if (master) "Мастер-пароль" else "Пароль файла", "b-password", secret = !show) {
                    FieldAction(if (show) AppIcons.VisibilityOff else AppIcons.Visibility, if (show) "Скрыть пароль" else "Показать пароль", "b-show") { show = !show }
                    // без похожих символов и спецсимволов: пароль файла чаще переписывают на бумагу, чем вставляют
                    if (!master) FieldAction(AppIcons.Generate, "Сгенерировать пароль файла", "b-generate") {
                        generatePassword(24u, true, true, true, false, true).also { password = it; repeat = it }
                        show = true
                    }
                }
                if (!master) Field(repeat, { repeat = it }, "Пароль еще раз", "b-repeat", secret = !show)
                Button(
                    {
                        error = null
                        if (!master && password != repeat) {
                            error = "пароли не совпадают"
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
                ) { Text("Сохранить файл") }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Импорт из файла", style = MaterialTheme.typography.titleSmall)
                Text("Записи из файла сливаются с хранилищем: более новые правки не затираются, повторный импорт ничего не меняет.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, Modifier.fillMaxWidth().tag("b-file")) {
                    Text(importUri?.let { displayName(context, it) } ?: "Выбрать файл", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Field(importPassword, { importPassword = it }, "Пароль файла", "b-import-password", secret = true)
                Button(
                    {
                        val uri = importUri ?: return@Button
                        error = null
                        busy = true
                        controller.importFrom(uri, importPassword, onError = { error = it; busy = false }) { onClose() }
                    },
                    Modifier.tag("b-import"),
                    enabled = !busy && importUri != null && importPassword.isNotEmpty(),
                ) { Text("Импортировать") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.tag("b-error")) }
            }
        },
    )
}

private const val NEW = "new"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(controller: VaultController, item: Item?, navigate: (String?) -> Unit) {
    val context = LocalContext.current
    val values = remember(item?.id) { mutableStateOf(dev.inkov.inkvault.android.FIELDS.associateWith { item?.field(it).orEmpty() }) }
    var tags by remember(item?.id) { mutableStateOf(item?.userTags?.joinToString(", ").orEmpty()) }
    var favorite by remember(item?.id) { mutableStateOf(item?.favorite ?: false) }
    var pinMode by remember(item?.id) { mutableStateOf(item?.field(PASSWORD_MODE) == PIN_TOTP) }
    var showPassword by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }
    var generator by remember { mutableStateOf(false) }
    fun set(name: String, value: String) {
        values.value = values.value + (name to value)
    }
    val v = values.value
    val deleted = item?.deleted ?: false
    val secret = v["totp"].orEmpty().trim()
    val secretError = if (secret.isEmpty()) null else runCatching { totp(secret, 0u) }.exceptionOrNull()?.message

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (item == null) "Новая запись" else v["name"].orEmpty().ifEmpty { "(без названия)" } + if (deleted) " (в корзине)" else "") },
            navigationIcon = { TextButton({ navigate(null) }, Modifier.tag("back")) { Text("Назад") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(v["name"].orEmpty(), { set("name", it) }, "Название", "f-name")
            Field(v["url"].orEmpty(), { set("url", it) }, "Адрес", "f-url")
            Field(v["username"].orEmpty(), { set("username", it) }, "Логин", "f-username") {
                FieldAction(AppIcons.Copy, "Копировать логин", "copy-username") { controller.notice(copySecret(context, "логин", v["username"].orEmpty())) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "Обычный пароль", true to "PIN + код").forEachIndexed { i, (pin, label) ->
                    SegmentedButton(
                        selected = pinMode == pin,
                        onClick = { pinMode = pin },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        modifier = Modifier.tag(if (pin) "mode-pin-totp" else "mode-password"),
                    ) { Text(label) }
                }
            }
            val what = if (pinMode) "PIN" else "пароль"
            Field(
                v["password"].orEmpty(), { set("password", it) }, if (pinMode) "PIN" else "Пароль", "f-password",
                secret = !showPassword, hint = if (pinMode) "Можно оставить пустым" else null,
            ) {
                FieldAction(if (showPassword) AppIcons.VisibilityOff else AppIcons.Visibility, if (showPassword) "Скрыть $what" else "Показать $what", "show-password") { showPassword = !showPassword }
                FieldAction(AppIcons.Copy, "Копировать $what", "copy-password") { controller.notice(copySecret(context, what, v["password"].orEmpty())) }
                FieldAction(AppIcons.Generate, "Сгенерировать $what", "gen-password") { generator = true }
            }
            // секрет TOTP нужен только для пароля PIN + код; в обычном режиме сохраненное значение не меняется
            if (pinMode) {
                Field(
                    v["totp"].orEmpty(), { set("totp", it) }, "Секрет TOTP", "f-totp",
                    secret = !showSecret, hint = secretError ?: "base32 или ссылка otpauth://totp/...", error = secretError != null,
                ) {
                    FieldAction(if (showSecret) AppIcons.VisibilityOff else AppIcons.Visibility, if (showSecret) "Скрыть секрет" else "Показать секрет", "show-totp") { showSecret = !showSecret }
                }
                if (secret.isNotEmpty() && secretError == null) OtpField(controller, v["password"].orEmpty(), secret)
            }
            Field(v["notes"].orEmpty(), { set("notes", it) }, "Заметка", "f-notes", singleLine = false)
            Field(tags, { tags = it }, "Теги", "f-tags", hint = "Через запятую, иерархия через /")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(favorite, { favorite = it }, Modifier.tag("f-favorite"))
                Text("Избранное")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!deleted) {
                    Button({
                        val problem = when {
                            !pinMode -> null
                            secret.isEmpty() -> "для пароля PIN + код нужен секрет TOTP"
                            else -> secretError
                        }
                        if (problem != null) {
                            controller.notice("не сохранено: $problem")
                            return@Button
                        }
                        var fields = if (pinMode) v + ("totp" to secret) else v
                        // режим пишется только при смене: незнакомое значение от более нового клиента не затирается
                        if (pinMode != (item?.field(PASSWORD_MODE) == PIN_TOTP)) fields = fields + (PASSWORD_MODE to if (pinMode) PIN_TOTP else "")
                        val tagList = tags.split(",").map { it.trim().trim('/') }.filter { it.isNotEmpty() } + if (favorite) listOf(FAVORITE) else emptyList()
                        if (item == null) {
                            controller.create(fields.filterValues { it.isNotEmpty() }, tagList) { id -> navigate(id) }
                        } else {
                            controller.update(item.id, fields, tagList)
                        }
                        controller.notice(if (item == null) "запись создана" else "запись сохранена")
                    }, Modifier.tag("save")) { Text("Сохранить") }
                }
                if (item != null && !deleted) OutlinedButton({ controller.delete(item.id); navigate(null) }, Modifier.tag("delete")) { Text("В корзину") }
                if (item != null && deleted) Button({ controller.restore(item.id); navigate(null) }, Modifier.tag("restore")) { Text("Восстановить") }
            }
            // одно значение может встретиться в истории несколько раз (docs/protocol.md в inkvault-core), повторы скрыты
            val history = item?.history?.distinct()?.filter { it != item.field("password") }.orEmpty()
            if (history.isNotEmpty()) {
                Text("История паролей (${history.size})", style = MaterialTheme.typography.titleSmall)
                history.forEach { old ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("•".repeat(minOf(old.length, 16)), Modifier.weight(1f))
                        TextButton({ controller.notice(copySecret(context, "старый пароль", old)) }) { Text("Копировать") }
                    }
                }
            }
        }
    }
    if (generator) GeneratorDialog(onUse = { set("password", it) }) { generator = false }
}

// Готовый пароль не хранится: код TOTP меняется каждые 30 с, поэтому он считается при показе и копировании
@Composable
private fun OtpField(controller: VaultController, pin: String, secret: String) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    val code = runCatching { totp(secret, now.toULong()) }.getOrNull() ?: return
    OutlinedTextField(
        value = pin + code.code,
        onValueChange = {},
        readOnly = true,
        label = { Text("Готовый пароль: PIN и код") },
        leadingIcon = {
            CircularProgressIndicator(progress = { code.remaining.toFloat() / code.period.toFloat() }, Modifier.size(22.dp), strokeWidth = 3.dp)
        },
        trailingIcon = {
            Row {
                FieldAction(if (shown) AppIcons.VisibilityOff else AppIcons.Visibility, if (shown) "Скрыть готовый пароль" else "Показать готовый пароль", "otp-show") { shown = !shown }
                FieldAction(AppIcons.Copy, "Копировать готовый пароль", "otp-copy") {
                    controller.notice(copySecret(context, "пароль", pin + totp(secret, System.currentTimeMillis().toULong()).code))
                }
            }
        },
        supportingText = { Text("обновится через ${code.remaining} с", Modifier.tag("otp-remaining")) },
        singleLine = true,
        // моноширинный шрифт только для открытого пароля: скрытые точки выглядят как в остальных полях
        textStyle = if (shown) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace) else LocalTextStyle.current,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().tag("otp-value"),
    )
}

private fun strength(bits: Double) = when {
    bits < 60 -> "слабый"
    bits < 80 -> "средний"
    bits < 100 -> "сильный"
    else -> "очень сильный"
}

@Composable
private fun GeneratorDialog(onUse: ((String) -> Unit)?, onClose: () -> Unit) {
    val context = LocalContext.current
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
        title = { Text("Генератор паролей") },
        text = {
            Column(Modifier.widthIn(max = 480.dp)) {
                result.onSuccess { (password, bits) ->
                    Text(password, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.tag("g-value"))
                    Text("${bits.toInt()} бит, ${strength(bits)}", style = MaterialTheme.typography.bodySmall)
                }.onFailure { Text(it.message.orEmpty(), color = MaterialTheme.colorScheme.error) }
                Text("Длина: ${length.toInt()}")
                Slider(length, { length = it }, valueRange = 8f..64f, modifier = Modifier.tag("g-length"))
                listOf(
                    Triple("строчные a-z", lower) { b: Boolean -> lower = b },
                    Triple("заглавные A-Z", upper) { b: Boolean -> upper = b },
                    Triple("цифры 0-9", digits) { b: Boolean -> digits = b },
                    Triple("символы !#$%", symbols) { b: Boolean -> symbols = b },
                    Triple("без похожих: I l 1 O 0 o |", ambiguous) { b: Boolean -> ambiguous = b },
                ).forEach { (label, checked, change) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, change)
                        Text(label)
                    }
                }
                Row {
                    TextButton({ counter++ }) { Text("Еще") }
                    TextButton({ result.onSuccess { context.let { c -> copySecret(c, "пароль", it.first) } } }) { Text("Копировать") }
                }
            }
        },
        confirmButton = {
            if (onUse != null) {
                TextButton({ result.onSuccess { onUse(it.first) }; onClose() }, Modifier.tag("g-use")) { Text("Вставить в запись") }
            }
        },
        dismissButton = { TextButton(onClose) { Text("Закрыть") } },
    )
}
