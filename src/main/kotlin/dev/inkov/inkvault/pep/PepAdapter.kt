package dev.inkov.inkvault.pep

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.packet.XmlElement
import org.jivesoftware.smack.util.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import uniffi.inkvault_uniffi.VaultSync

const val NS_PUBSUB = "http://jabber.org/protocol/pubsub"
const val NS_OWNER = "http://jabber.org/protocol/pubsub#owner"
const val NS_PUBSUB_ERRORS = "http://jabber.org/protocol/pubsub#errors"
const val NS_VAULT = "urn:inkvault:vault:0"
const val NS_ACCOUNT = "urn:inkvault:account:0"

// В PEP-менеджере Smack нет publish-options и настройки узла, поэтому IQ собирается из готового XML
class RawPubSubIq(type: Type, namespace: String, private val inner: String) : IQ("pubsub", namespace) {
    init {
        setType(type)
    }

    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
        xml.rightAngleBracket()
        xml.append(inner)
        return xml
    }
}

private fun parse(xml: String): Element = DocumentBuilderFactory.newInstance()
    .apply { isNamespaceAware = true }
    .newDocumentBuilder()
    .parse(InputSource(StringReader(xml)))
    .documentElement

/** Учетная запись из узла PEP (JSON из ядра) или null, если узла еще нет. Нужна до создания движка. */
fun fetchAccount(conn: XMPPConnection): String? {
    val response = try {
        conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.get, NS_PUBSUB, "<items node='$NS_ACCOUNT'/>"))
    } catch (e: XMPPErrorException) {
        if (e.stanzaError.condition == StanzaError.Condition.item_not_found) return null
        throw e
    }
    return parse(response.toXML().toString()).getElementsByTagNameNS(NS_ACCOUNT, "account").item(0)?.textContent
}

fun publishAccount(conn: XMPPConnection, accountJson: String) {
    val inner = "<publish node='$NS_ACCOUNT'><item id='current'>" +
        "<account xmlns='$NS_ACCOUNT' v='1'>${StringUtils.escapeForXmlText(accountJson)}</account>" +
        "</item></publish>" +
        "<publish-options>${PepAdapter.form("http://jabber.org/protocol/pubsub#publish-options", 1)}</publish-options>"
    conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.set, NS_PUBSUB, inner))
}

/** Есть ли в узле хранилища записи. Нужно перед созданием учетной записи: записи без нее - признак потери узла учетной записи. */
fun hasVaultRecords(conn: XMPPConnection): Boolean {
    val response = try {
        conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.get, NS_PUBSUB, "<items node='$NS_VAULT' max_items='1'/>"))
    } catch (e: XMPPErrorException) {
        if (e.stanzaError.condition == StanzaError.Condition.item_not_found) return false
        throw e
    }
    return parse(response.toXML().toString()).getElementsByTagNameNS("*", "item").length > 0
}

/** Удаление узла со всеми элементами и настройками (запрос владельца). Только по явному решению пользователя. */
fun deleteNode(conn: XMPPConnection, node: String) {
    try {
        conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.set, NS_OWNER, "<delete node='$node'/>"))
    } catch (e: XMPPErrorException) {
        if (e.stanzaError.condition != StanzaError.Condition.item_not_found) throw e
    }
}

/**
 * Адаптер PEP для движка синхронизации (VaultSync): исполняет команды движка через Smack
 * и передает ему ответы сервера. Уведомления о новых элементах платформа передает в [onItem].
 */
class PepAdapter(
    private val conn: XMPPConnection,
    val engine: VaultSync,
    private val onEvent: (JSONObject) -> Unit = {},
    private val now: () -> ULong = { System.currentTimeMillis().toULong() },
) {
    fun connected(resumed: Boolean): JSONObject = apply(engine.connected(resumed, null))

    fun onItem(id: String, keyVersion: Int, ciphertext: String): JSONObject =
        apply(engine.itemNotified(item(id, keyVersion, ciphertext).toString(), now()))

    /** Обрабатывает результат вызова движка: события отдает в onEvent, команды исполняет по очереди. */
    fun apply(outputJson: String): JSONObject {
        val output = JSONObject(outputJson)
        val events = output.getJSONArray("events")
        for (i in 0 until events.length()) onEvent(events.getJSONObject(i))
        val commands = output.getJSONArray("commands")
        for (i in 0 until commands.length()) execute(commands.getJSONObject(i))
        return output
    }

    private fun execute(command: JSONObject) {
        val request = command.optInt("request").toUInt()
        when (val op = command.getString("op")) {
            "fetch_config" -> apply(engine.configFetched(request, nodeMaxItems(command.getString("node"))))
            "fetch_all" -> apply(engine.itemsFetched(fetch(command.getString("node")).toString(), now()))
            "publish" -> apply(engine.publishResult(request, publish(command)))
            "configure_node" -> apply(engine.configureResult(request, configure(command)))
            else -> error("неизвестная команда движка: $op")
        }
    }

    // max_items из настроек узла (запрос владельца); null - узла нет или настройки не прочитались
    private fun nodeMaxItems(node: String): UInt? {
        val response = try {
            conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.get, NS_OWNER, "<configure node='$node'/>"))
        } catch (e: XMPPErrorException) {
            return null
        }
        val fields = parse(response.toXML().toString()).getElementsByTagNameNS("*", "field")
        for (i in 0 until fields.length) {
            val field = fields.item(i) as Element
            if (field.getAttribute("var") != "pubsub#max_items") continue
            return field.getElementsByTagNameNS("*", "value").item(0)?.textContent?.trim()?.toUIntOrNull()?.takeIf { it > 0u }
        }
        return null
    }

    private fun fetch(node: String): JSONArray {
        val response = try {
            conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.get, NS_PUBSUB, "<items node='$node'/>"))
        } catch (e: XMPPErrorException) {
            // узла еще нет: учетная запись без записей
            if (e.stanzaError.condition == StanzaError.Condition.item_not_found) return JSONArray()
            throw e
        }
        val root = parse(response.toXML().toString())
        val result = JSONArray()
        // Smack при повторной сериализации меняет пространство имен item, поэтому поиск без привязки к нему
        val items = root.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val entry = item.getElementsByTagNameNS(NS_VAULT, "entry").item(0) as? Element ?: continue
            result.put(item(item.getAttribute("id"), entry.getAttribute("key").toInt(), entry.textContent.trim()))
        }
        return result
    }

    private fun publish(command: JSONObject): String? {
        val item = command.getJSONObject("item")
        val inner = "<publish node='${command.getString("node")}'><item id='${item.getString("id")}'>" +
            "<entry xmlns='$NS_VAULT' v='1' key='${item.getInt("key_version")}'>${item.getString("ciphertext")}</entry>" +
            "</item></publish>" +
            "<publish-options>${form("http://jabber.org/protocol/pubsub#publish-options", command.getInt("max_items"))}</publish-options>"
        return try {
            conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.set, NS_PUBSUB, inner))
            null
        } catch (e: XMPPErrorException) {
            condition(e.stanzaError)
        }
    }

    private fun configure(command: JSONObject): Boolean {
        val inner = "<configure node='${command.getString("node")}'>" +
            form("http://jabber.org/protocol/pubsub#node_config", command.getInt("max_items")) + "</configure>"
        return try {
            conn.sendIqRequestAndWaitForResponse<IQ>(RawPubSubIq(IQ.Type.set, NS_OWNER, inner))
            true
        } catch (e: XMPPErrorException) {
            false
        }
    }

    companion object {
        fun item(id: String, keyVersion: Int, ciphertext: String): JSONObject =
            JSONObject().put("id", id).put("key_version", keyVersion).put("ciphertext", ciphertext)

        // Параметры узла хранилища: при расхождении с ними сервер отклоняет публикацию
        internal fun form(formType: String, maxItems: Int): String =
            "<x xmlns='jabber:x:data' type='submit'>" +
                "<field var='FORM_TYPE' type='hidden'><value>$formType</value></field>" +
                "<field var='pubsub#access_model'><value>whitelist</value></field>" +
                "<field var='pubsub#persist_items'><value>true</value></field>" +
                "<field var='pubsub#max_items'><value>$maxItems</value></field>" +
                "</x>"

        // Специфичное условие PubSub (precondition-not-met) важнее общего (conflict); текст сервера объясняет причину
        private fun condition(error: StanzaError): String {
            val condition = if (error.getExtension<XmlElement>("precondition-not-met", NS_PUBSUB_ERRORS) != null) {
                "precondition-not-met"
            } else {
                error.condition.toString()
            }
            return error.descriptiveText?.let { "$condition: $it" } ?: condition
        }
    }
}
