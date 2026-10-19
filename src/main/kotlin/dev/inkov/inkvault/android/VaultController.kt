package dev.inkov.inkvault.android

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.inkov.inkvault.pep.NS_ACCOUNT
import dev.inkov.inkvault.pep.NS_VAULT
import dev.inkov.inkvault.pep.PepAdapter
import dev.inkov.inkvault.pep.deleteNode
import dev.inkov.inkvault.pep.fetchAccount
import dev.inkov.inkvault.pep.hasVaultRecords
import dev.inkov.inkvault.pep.publishAccount
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Executors
import javax.net.ssl.SSLException
import org.jivesoftware.smack.ConnectionConfiguration.DnssecMode
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.SmackConfiguration
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.sasl.SASLErrorException
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.DNSUtil
import org.jivesoftware.smack.util.dns.minidns.MiniDnsResolver
import org.jivesoftware.smack.util.rce.RemoteConnectionEndpointLookupFailure
import org.jivesoftware.smackx.pep.PepManager
import org.json.JSONArray
import org.json.JSONObject
import org.minidns.dnsname.DnsName
import uniffi.inkvault_uniffi.AccountSync
import uniffi.inkvault_uniffi.VaultAccount
import uniffi.inkvault_uniffi.VaultSync
import uniffi.inkvault_uniffi.accountIsNewer
import uniffi.inkvault_uniffi.defaultMaxItems
import uniffi.inkvault_uniffi.generateNodeId

private const val RETRY_MS = 10_000L
// блокировка после 15 минут без действий пользователя, время в фоне тоже считается
private const val AUTO_LOCK_MS = 15 * 60_000L
// без этого лимита выбранный по ошибке большой файл занял бы память приложения; 5000 записей - около 7 МБ
private const val IMPORT_MAX_BYTES = 64 * 1024 * 1024

/** orphans - в узле хранилища есть записи без учетной записи: создание требует подтверждения. */
class NoVaultException(val orphans: Boolean, message: String) : Exception(message)

// при полной загрузке узла предупреждения о каждой записи сводятся в одно уведомление
private val WARNING_SUMMARY = mapOf(
    "missing_on_server" to R.string.warning_missing_on_server,
    "unreadable" to R.string.warning_unreadable,
)

/** Подключение не удалось: в message короткая причина для экрана, исходное исключение - в cause. */
class ConnectionFailed(message: String, cause: Throwable) : Exception(message, cause)

// Тексты исключений Smack длинные и на английском: на экран идет причина, подробности - в журнал
private fun describe(context: Context, e: Throwable): String = when {
    // Error из библиотеки - не сетевая причина: на экран идут класс и место ошибки, без adb их больше не узнать
    e is Error -> context.getString(
        R.string.error_internal,
        "$e " + e.stackTrace.take(3).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" },
    )
    e is SASLErrorException -> context.getString(R.string.error_auth)
    e is UnknownHostException -> context.getString(R.string.error_host_not_found)
    e is SmackException.EndpointConnectionException && e.connectionExceptions.isEmpty() -> context.getString(R.string.error_host_not_found)
    generateSequence<Throwable>(e) { it.cause }.any { it is SSLException } -> context.getString(R.string.error_tls)
    else -> context.getString(R.string.error_unreachable)
}

// MiniDNS не проходит CNAME в цели SRV и возвращает пустой список. Smack делает из него точку подключения
// без адресов, и assert в XMPPTCPConnection завершает подключение с AssertionError (assert включен в debug-сборке).
// Системный резолвер CNAME проходит; null вместо пустого списка - Smack такую точку не создает
private class SystemFallbackResolver : MiniDnsResolver() {
    override fun lookupHostAddress0(
        name: DnsName,
        lookupFailures: MutableList<RemoteConnectionEndpointLookupFailure>,
        dnssecMode: DnssecMode,
    ): List<InetAddress>? {
        val found = super.lookupHostAddress0(name, lookupFailures, dnssecMode)
        if (!found.isNullOrEmpty()) return found
        return try {
            InetAddress.getAllByName(name.ace).toList()
        } catch (e: UnknownHostException) {
            lookupFailures.add(RemoteConnectionEndpointLookupFailure.DnsLookupFailure(name, e))
            null
        }
    }
}

/**
 * Состояние клиента для интерфейса и вся работа с ядром и сетью. Ядро и Smack вызываются только
 * из рабочего потока: синхронные IQ не блокируют главный поток и потоки Smack, а движок не
 * получает вызовы из нескольких потоков одновременно.
 */
class VaultController(private val context: Context) {
    var screen by mutableStateOf<Screen>(Screen.Login)
        private set
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var status by mutableStateOf(Status.OFFLINE)
        private set
    var pending by mutableStateOf(0)
        private set
    // лимит записей узла хранилища: с сервера или, пока узла нет, лимит для его создания; обновляется в refresh
    var maxItems by mutableStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var biometricEnabled by mutableStateOf(false)
        private set
    // причина последней неудачной попытки подключения и время следующей, 0 - повтора нет
    var connectionProblem by mutableStateOf<String?>(null)
        private set
    var retryAt by mutableStateOf(0L)
        private set
    // null - хранилище есть или вход не выполнялся; true - в узле хранилища записи без учетной записи
    var noVault by mutableStateOf<Boolean?>(null)
        private set
    val notices = mutableStateListOf<String>()

    private val store = Store(context)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var account: VaultAccount? = null
    private var engine: VaultSync? = null
    private var vaultId: String? = null
    private var conn: XMPPTCPConnection? = null
    private var adapter: PepAdapter? = null
    @Volatile private var locked = true
    // состояние соединения для рабочего потока; status - его копия для интерфейса
    @Volatile private var online = false
    // уведомление об ошибке подключения - только при первой ошибке серии или смене причины
    private var shownProblem: String? = null
    private val retry = Runnable { if (!locked) worker.execute { connectInBackground() } }
    // elapsedRealtime, а не uptime: время сна устройства тоже считается
    private var lastActivity = 0L
    private val autoLock = Runnable { checkAutoLock() }
    // учетная запись с сервера при конфликте: на нее можно перейти по решению пользователя
    @Volatile private var foreignAccount: String? = null
    // состояние конфликта для рабочего потока: уведомление - только при входе в конфликт
    private var conflict = false
    private var warnings: MutableMap<String, Pair<Int, String>>? = null

    val settings: Settings? get() = store.settings()

    init {
        AndroidSmackInitializer.initialize(context)
        // первое обращение к SmackConfiguration запускает инициализацию Smack, которая регистрирует
        // MiniDnsResolver: свой резолвер ставится после нее, иначе инициализация его заменит
        SmackConfiguration.getVersion()
        DNSUtil.setDNSResolver(SystemFallbackResolver())
        ProviderManager.addExtensionProvider("entry", NS_VAULT, EntryProvider())
        screen = if (store.account() != null) Screen.Unlock else Screen.Login
        biometricEnabled = Biometric.supported && store.device() != null
    }

    fun notice(text: String) = main.post {
        notices.add(text)
        main.postDelayed({ notices.remove(text) }, 3000)
    }

    fun notice(@StringRes id: Int, vararg args: Any) = notice(str(id, *args))

    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun ui(action: () -> Unit) = main.post(action)

    /** Действие в рабочем потоке: ошибка показывается на экране, индикатор занятости снимается. */
    private fun background(action: () -> Unit) {
        busy = true
        error = null
        worker.execute {
            try {
                action()
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                ui { error = message }
            } finally {
                ui { busy = false }
            }
        }
    }

    /**
     * Первый вход на устройстве: учетная запись берется с сервера или создается (create). Если в узле
     * хранилища остались записи без учетной записи, нужно еще replaceOrphans: эти записи удаляются.
     */
    fun login(settings: Settings, xmppPassword: String, masterPassword: String, create: Boolean, replaceOrphans: Boolean = false) = background {
        ui { noVault = null }
        val connection = connect(settings, xmppPassword)
        val json = fetchAccount(connection)
        val opened = if (json != null) {
            VaultAccount.unlock(json, masterPassword)
        } else {
            val orphans = hasVaultRecords(connection)
            if (!create || (orphans && !replaceOrphans)) {
                connection.disconnect()
                ui { noVault = orphans }
                throw NoVaultException(orphans, str(if (orphans) R.string.no_vault_orphans else R.string.no_vault))
            }
            VaultAccount.create(masterPassword, now()).also {
                // записи прежней учетной записи новой не открыть, они только мешали бы при каждой загрузке узла
                if (orphans) deleteNode(connection, NS_VAULT)
                publishAccount(connection, it.accountJson())
            }
        }
        store.saveSettings(settings)
        store.saveXmppPassword(xmppPassword)
        store.saveAccount(opened.accountJson())
        open(opened)
        attach(connection)
    }

    fun unlock(masterPassword: String) = background {
        val saved = store.account() ?: error(str(R.string.no_local_account))
        val local = runCatching { VaultAccount.unlock(saved, masterPassword) }
        local.getOrNull()?.let {
            open(it)
            connectInBackground()
            return@background
        }
        val (opened, connection) = unlockFromServer(saved, masterPassword) ?: throw Exception(local.exceptionOrNull()?.message)
        open(opened)
        attach(connection)
    }

    /**
     * Копия учетной записи на устройстве не открылась: мастер-пароль могли сменить на другом устройстве.
     * Тогда подходит только более новая копия той же учетной записи с сервера, null - такой нет.
     */
    private fun unlockFromServer(saved: String, masterPassword: String): Pair<VaultAccount, XMPPTCPConnection>? {
        val settings = store.settings() ?: return null
        val password = runCatching { store.xmppPassword() }.getOrNull() ?: return null
        val connection = runCatching { connect(settings, password) }.getOrNull() ?: return null
        val opened = runCatching {
            fetchAccount(connection)?.takeIf { accountIsNewer(it, saved) }?.let { VaultAccount.unlock(it, masterPassword) }
        }.getOrNull()
        if (opened == null) {
            runCatching { connection.disconnect() }
            ui { status = Status.OFFLINE }
            return null
        }
        store.saveAccount(opened.accountJson())
        return opened to connection
    }

    fun unlockWithBiometrics(activity: Activity) {
        val (encrypted, blob) = store.device() ?: return
        try {
            Biometric.decrypt(activity, encrypted, { secret ->
                background {
                    val opened = VaultAccount.unlockWithDevice(store.account()!!, secret, blob)
                    secret.fill(0)
                    open(opened)
                    connectInBackground()
                }
            }, { notice(it) })
        } catch (e: Biometric.Invalidated) {
            store.clearDevice()
            biometricEnabled = false
            error = str(R.string.biometric_invalidated)
        }
    }

    fun enableBiometrics(activity: Activity) {
        val current = account ?: return
        worker.execute {
            val enrollment = runCatching { current.enrollDevice() }.getOrElse { notice(it.message ?: str(R.string.error)); return@execute }
            ui {
                Biometric.encrypt(activity, enrollment.secret, { encrypted ->
                    enrollment.secret.fill(0)
                    store.saveDevice(encrypted, enrollment.blob)
                    biometricEnabled = true
                    notice(R.string.biometric_enabled)
                }, { notice(it) })
            }
        }
    }

    /** Почему лимит записей не подходит, null - подходит. */
    fun maxItemsProblem(value: Int?): String? = when {
        value == null || value !in MAX_ITEMS_RANGE -> str(R.string.limit_range, MAX_ITEMS_RANGE.first, MAX_ITEMS_RANGE.last)
        // при уменьшении max_items Prosody удаляет из узла самые старые записи
        value < items.size -> str(R.string.limit_too_small, items.size)
        else -> null
    }

    /** Новый лимит записей узла. Узел перенастраивается сразу, поэтому нужна связь с сервером. */
    fun changeMaxItems(value: Int) = worker.execute {
        val e = engine ?: return@execute
        runCatching {
            maxItemsProblem(value)?.let { error(it) }
            if (!online) error(str(R.string.not_connected))
            apply(e.setMaxItems(value.toUInt()))
        }.onFailure { notice(it.message ?: it.javaClass.simpleName) }
        // отказ сервера приходит предупреждением движка, лимит при этом остается прежним
        if (e.maxItems() == value.toUInt()) notice(R.string.limit_changed, value)
        refresh()
    }

    fun disableBiometrics() {
        store.clearDevice()
        biometricEnabled = false
        notice(R.string.biometric_disabled)
    }

    fun userActive() {
        lastActivity = SystemClock.elapsedRealtime()
    }

    /**
     * Отложенная проверка переносится на остаток срока, а не на каждое касание. Handler не идет во время
     * сна устройства, поэтому проверка вызывается и при возврате в приложение.
     */
    fun checkAutoLock() {
        main.removeCallbacks(autoLock)
        if (locked) return
        val left = lastActivity + AUTO_LOCK_MS - SystemClock.elapsedRealtime()
        if (left > 0) main.postDelayed(autoLock, left) else lock()
    }

    /** Блокировка: соединение закрывается, ключи в ядре освобождаются, кэш остается на устройстве. */
    fun lock() {
        locked = true
        online = false
        main.removeCallbacks(retry)
        main.removeCallbacks(autoLock)
        connectionProblem = null
        retryAt = 0
        // экран закрывается сразу: рабочий поток может быть занят подключением, а записи не должны быть видны до его освобождения
        screen = Screen.Unlock
        worker.execute {
            shownProblem = null
            conflict = false
            save()
            runCatching { conn?.disconnect() }
            conn = null
            adapter = null
            engine?.destroy()
            account?.destroy()
            engine = null
            account = null
            ui {
                items = emptyList()
                status = Status.OFFLINE
            }
        }
    }

    fun forget() {
        lock()
        worker.execute {
            store.forget()
            ui {
                biometricEnabled = false
                screen = Screen.Login
            }
        }
    }

    fun create(fields: Map<String, String>, tags: List<String>, onDone: (String) -> Unit) = withEngine { e ->
        val output = JSONObject(e.create("login", JSONObject(fields).toString(), now()))
        val id = output.getString("item_id")
        apply(output.toString())
        if (tags.isNotEmpty()) apply(e.editItem(id, "{}", JSONObject(tags.associateWith { true }).toString(), now()))
        ui { onDone(id) }
    }

    fun update(id: String, fields: Map<String, String>, tags: List<String>) = withEngine { e ->
        val current = items.firstOrNull { it.id == id } ?: return@withEngine
        val changes = tags.associateWith { true } + current.tags.filterNot { it in tags }.associateWith { false }
        apply(e.editItem(id, JSONObject(fields).toString(), JSONObject(changes).toString(), now()))
    }

    /**
     * Файл экспорта готовится до выбора места сохранения: под мастер-паролем пароль проверяется
     * заранее, и при ошибке пустой файл не создается.
     */
    fun prepareExport(password: String, master: Boolean, onError: (String) -> Unit, onReady: (String) -> Unit) = worker.execute {
        val e = engine ?: return@execute
        runCatching {
            if (master && account?.verifyPassword(password) != true) error(str(R.string.wrong_master_password))
            e.exportVault(password, now())
        }.onSuccess { ui { onReady(it) } }.onFailure { ui { onError(it.message ?: it.javaClass.simpleName) } }
    }

    fun writeExport(uri: Uri, file: String) = worker.execute {
        val count = engine?.let { JSONArray(it.records()).length() } ?: 0
        runCatching {
            (context.contentResolver.openOutputStream(uri, "wt") ?: error(str(R.string.file_not_writable))).use { it.write(file.toByteArray()) }
        }.onSuccess { notice(R.string.export_saved, count) }.onFailure { notice(R.string.file_not_saved, it.message.orEmpty()) }
    }

    /** Импорт файла экспорта: слияние по HLC, новые и измененные записи публикуются. */
    fun importFrom(uri: Uri, password: String, onError: (String) -> Unit, onDone: () -> Unit) = worker.execute {
        val e = engine ?: return@execute
        runCatching {
            val output = JSONObject(e.importVault(readLimited(uri), password, now()))
            apply(output.toString())
            refresh()
            output.getJSONObject("imported")
        }.onSuccess { stats ->
            val (added, updated, unchanged, skipped) = listOf("added", "updated", "unchanged", "skipped").map(stats::getInt)
            if (skipped > 0) notice(R.string.import_result_skipped, added, updated, unchanged, skipped)
            else notice(R.string.import_result, added, updated, unchanged)
            ui { onDone() }
        }.onFailure { ui { onError(it.message ?: it.javaClass.simpleName) } }
    }

    private fun readLimited(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: error(str(R.string.file_not_readable))
        val out = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > IMPORT_MAX_BYTES) error(str(R.string.file_too_large))
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /**
     * Смена мастер-пароля, только при связи с сервером. Учетная запись сначала сверяется с сервером:
     * смена на двух устройствах без сверки дала бы две версии с одним rev, и одна из них потерялась бы.
     * Ключ отпечатка остается рабочим: он оборачивает user key, а не ключ из пароля.
     */
    fun changePassword(current: String, newPassword: String, onError: (String) -> Unit, onDone: () -> Unit) = worker.execute {
        val c = conn
        val a = account
        runCatching {
            if (c == null || a == null || !online) error(str(R.string.not_connected))
            val server = fetchAccount(c)
            if (server != null && a.refresh(server) == AccountSync.FOREIGN) error(str(R.string.account_foreign))
            val json = a.changePassword(current, newPassword)
            store.saveAccount(json)
            // копия на устройстве уже новая: при следующей сверке ядро выберет ее и потребует публикации
            runCatching { publishAccount(c, json) }.onFailure {
                error(str(R.string.password_not_published, it.message.orEmpty()))
            }
        }.onSuccess {
            notice(R.string.password_changed)
            ui { onDone() }
        }.onFailure { ui { onError(it.message ?: it.javaClass.simpleName) } }
    }

    /** Заменить чужую учетную запись на сервере своей: записи с сервера удаляются, свои публикуются заново. */
    fun replaceServerAccount() = worker.execute {
        val c = conn ?: return@execute
        val pep = adapter ?: return@execute
        val current = account ?: return@execute
        runCatching {
            deleteNode(c, NS_VAULT)
            publishAccount(c, current.accountJson())
            engine?.sessionLost()
            sync(pep, false)
        }.onSuccess { notice(R.string.account_replaced) }.onFailure { notice(R.string.account_replace_failed, it.message.orEmpty()) }
    }

    /**
     * Перейти на учетную запись с сервера: она становится учетной записью устройства, копия прежней
     * сохраняется отдельно. Ключ отпечатка относится к прежней учетной записи и удаляется.
     */
    fun switchToServerAccount() {
        val json = foreignAccount ?: return
        lock()
        worker.execute {
            store.replaceAccount(json)
            store.clearDevice()
            ui { biometricEnabled = false }
        }
    }

    fun delete(id: String) = withEngine { e -> apply(e.deleteItem(id, now())) }

    fun restore(id: String) = withEngine { e -> apply(e.restoreItem(id, now())) }

    /** Очистка корзины: записи стираются на всех устройствах, восстановить их нельзя. */
    fun purgeTrash() = withEngine { e ->
        val count = Item.parse(e.records()).count { it.deleted }
        apply(e.purgeTrash(now()))
        notice(R.string.trash_purged, count)
    }

    /**
     * Полное удаление хранилища: узлы учетной записи и записей удаляются с сервера, с устройства -
     * все, кроме настроек входа. При ошибке сервера хранилище блокируется, данные на устройстве остаются.
     */
    fun deleteVault(masterPassword: String, onError: (String) -> Unit) = worker.execute {
        val c = conn
        val problem = when {
            runCatching { account?.verifyPassword(masterPassword) }.getOrNull() != true -> str(R.string.wrong_master_password)
            c == null || !online -> str(R.string.not_connected)
            else -> null
        }
        if (problem != null) {
            ui { onError(problem) }
            return@execute
        }
        // публикация после удаления создала бы узел заново, поэтому движок отключается от соединения заранее
        locked = true
        online = false
        adapter = null
        val result = runCatching {
            deleteNode(c!!, NS_ACCOUNT)
            deleteNode(c, NS_VAULT)
        }
        ui {
            lock()
            worker.execute {
                result.onSuccess {
                    store.forgetVault()
                    notice(R.string.vault_deleted)
                    ui {
                        biometricEnabled = false
                        screen = Screen.Login
                    }
                }.onFailure { notice(R.string.vault_not_deleted, it.message.orEmpty()) }
            }
        }
    }

    private fun withEngine(action: (VaultSync) -> Unit) {
        worker.execute {
            val e = engine ?: return@execute
            runCatching { action(e) }.onFailure { notice(R.string.not_saved, it.message.orEmpty()) }
            refresh()
        }
    }

    private fun now() = System.currentTimeMillis().toULong()

    // все вызовы ниже - в рабочем потоке

    private fun open(opened: VaultAccount) {
        val id = JSONArray(opened.vaults()).getJSONObject(0).getString("id")
        val snapshot = store.snapshot(id)
        engine = snapshot?.let { runCatching { opened.restoreVault(id, NS_VAULT, defaultMaxItems(), it) }.getOrNull() }
            ?: opened.openVault(id, NS_VAULT, generateNodeId(), defaultMaxItems())
        account = opened
        vaultId = id
        locked = false
        // items обновляется в главном потоке позже, поэтому число записей берется из результата refresh
        val loaded = refresh()
        if (snapshot != null) notice(R.string.from_cache, loaded.size)
        ui {
            screen = Screen.Vault
            userActive()
            checkAutoLock()
        }
    }

    private fun refresh(): List<Item> {
        val e = engine ?: return emptyList()
        val list = Item.parse(e.records())
        val count = e.pending().toInt()
        val limit = e.maxItems().toInt()
        ui {
            items = list
            pending = count
            maxItems = limit
        }
        return list
    }

    private fun save() {
        val e = engine ?: return
        val id = vaultId ?: return
        runCatching { store.saveSnapshot(id, e.snapshot()) }
    }

    private fun apply(outputJson: String) {
        val a = adapter
        if (a != null && online) {
            a.apply(outputJson)
        } else {
            // без сети движок не выдает команд, публикация - после подключения
            JSONObject(outputJson).getJSONArray("events").let { events -> (0 until events.length()).forEach { onEvent(events.getJSONObject(it)) } }
        }
        save()
    }

    private fun onEvent(event: JSONObject) {
        when (event.getString("event")) {
            "item_changed" -> refresh()
            "warning" -> {
                val (kind, detail) = event.getString("kind") to event.getString("detail")
                val collected = warnings
                if (collected != null) {
                    collected[kind] = (collected[kind]?.first ?: 0) + 1 to detail
                } else {
                    notice(detail)
                }
            }
        }
    }

    private fun connect(settings: Settings, password: String): XMPPTCPConnection {
        ui { status = Status.CONNECTING }
        val (local, domain) = settings.jid.split("@").also { require(it.size == 2) { str(R.string.jid_format) } }
        val builder = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(domain)
            .setUsernameAndPassword(local, password)
            .setResource("android-${android.os.Build.MODEL.replace(' ', '-')}")
            .setSecurityMode(SecurityMode.required)
        try {
            // без адреса сервера Smack находит его по SRV-записям домена
            if (settings.host.isNotBlank()) {
                val address = InetAddress.getByName(settings.host)
                builder.setHostAddress(address).setPort(settings.port)
                // тестовый Prosody работает без TLS, телефон подключается к нему через adb reverse на 127.0.0.1
                if (BuildConfig.DEBUG && address.isLoopbackAddress) builder.setSecurityMode(SecurityMode.disabled)
            }
            val connection = XMPPTCPConnection(builder.build())
            connection.setUseStreamManagement(true)
            connection.setUseStreamManagementResumption(true)
            connection.connect().login()
            return connection
        } catch (e: Throwable) {
            // поиск сервера по SRV идет в этом потоке: Error из MiniDNS или Smack, не перехваченный здесь, завершает процесс
            Log.w("InkVault", "подключение к ${settings.jid} не удалось", e)
            ui { status = Status.OFFLINE }
            throw ConnectionFailed(describe(context, e), e)
        }
    }

    private fun connectInBackground() {
        // повтор, отложенный до блокировки, не должен открыть второе соединение
        if (locked || online) return
        val settings = store.settings() ?: return
        val password = store.xmppPassword() ?: return
        try {
            attach(connect(settings, password))
        } catch (e: Exception) {
            val reason = if (e is ConnectionFailed) e.message.orEmpty() else describe(context, e)
            // с неверным паролем повтор не поможет, а частые попытки входа сервер может заблокировать
            val again = e.cause !is SASLErrorException
            if (reason != shownProblem) notice(if (again) str(R.string.connection_retry, reason, RETRY_MS / 1000) else reason)
            shownProblem = reason
            ui {
                connectionProblem = reason
                retryAt = if (again) System.currentTimeMillis() + RETRY_MS else 0
            }
            main.removeCallbacks(retry)
            if (again) main.postDelayed(retry, RETRY_MS)
        }
    }

    private fun attach(connection: XMPPTCPConnection) {
        val e = engine ?: return
        conn = connection
        val pep = PepAdapter(connection, e, onEvent = ::onEvent)
        adapter = pep
        // переподключение - тем же путем, что первое подключение: новое соединение и повторы каждые 10 с.
        // Автопереподключение Smack после неудачной попытки, при которой TCP-соединение открылось и сразу
        // закрылось, считает соединение установленным и больше не пробует
        connection.addConnectionListener(object : ConnectionListener {
            override fun connectionClosedOnError(ex: Exception) {
                worker.execute {
                    if (adapter !== pep) return@execute
                    // новое соединение - новая сессия: уведомления за время обрыва пропали, узел загружается заново
                    e.sessionLost()
                    online = false
                    adapter = null
                    conn = null
                    runCatching { connection.disconnect() }
                    if (locked) return@execute
                    ui {
                        status = Status.OFFLINE
                        connectionProblem = str(R.string.connection_lost)
                        retryAt = System.currentTimeMillis() + 1000
                    }
                    main.removeCallbacks(retry)
                    main.postDelayed(retry, 1000)
                }
            }
        })
        val own = connection.user.asEntityBareJid()
        PepManager.getInstanceFor(connection).addPepEventListener(NS_VAULT, EntryElement::class.java) { from, entry, id, _ ->
            // PepManager пропускает событие PubSub от любого голого JID, а узел хранилища - только свой
            if (from != own) return@addPepEventListener
            worker.execute {
                if (adapter !== pep) return@execute
                runCatching { pep.onItem(id, entry.keyVersion, entry.ciphertext) }
                save()
                refresh()
            }
        }
        sync(pep, false)
    }

    private fun sync(pep: PepAdapter, resumed: Boolean) {
        val c = conn ?: return
        val current = account ?: return
        // учетная запись сверяется раньше записей: при чужой учетной записи записи не загружаются и не публикуются
        val server = try {
            fetchAccount(c)
        } catch (e: Exception) {
            Log.w("InkVault", "учетная запись с сервера не загружена", e)
            notice(R.string.sync_account_not_loaded)
            return
        }
        val state = if (server == null) AccountSync.PUBLISH else current.refresh(server)
        if (state == AccountSync.FOREIGN) {
            online = false
            foreignAccount = server
            if (!conflict) notice(R.string.sync_foreign)
            conflict = true
            ui { status = Status.CONFLICT }
            return
        }
        foreignAccount = null
        conflict = false
        if (state != AccountSync.SAME) store.saveAccount(current.accountJson())
        when (state) {
            AccountSync.PUBLISH -> {
                publishAccount(c, current.accountJson())
                notice(if (server == null) R.string.sync_account_restored else R.string.sync_account_outdated)
            }
            AccountSync.UPDATED -> notice(R.string.sync_account_updated)
            else -> {}
        }
        online = true
        shownProblem = null
        ui {
            status = Status.ONLINE
            connectionProblem = null
            retryAt = 0
        }
        warnings = mutableMapOf()
        runCatching { pep.connected(resumed) }.onFailure { notice(R.string.sync_error, it.message.orEmpty()) }
        warnings?.forEach { (kind, seen) ->
            val (count, detail) = seen
            notice(WARNING_SUMMARY[kind]?.let { str(it, count) } ?: if (count > 1) str(R.string.warning_repeated, detail, count) else detail)
        }
        warnings = null
        save()
        refresh()
    }
}
