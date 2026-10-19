package dev.inkov.inkvault.android

import androidx.annotation.StringRes
import org.json.JSONArray

const val FAVORITE = "@favorite"
// url - первый адрес сайта, urls - остальные по одному в строке: клиент без поддержки нескольких адресов работает с первым
val FIELDS = listOf("name", "url", "urls", "username", "password", "totp", "notes")

// пароль для входа - поле password и текущий код TOTP после него. Сам пароль хранится в password,
// поэтому его смены попадают в историю
const val PASSWORD_MODE = "password_mode"
const val PIN_TOTP = "pin_totp"

// CONFLICT - на сервере чужая учетная запись: записи не загружаются и не публикуются до решения пользователя
enum class Status(@StringRes val label: Int) {
    OFFLINE(R.string.status_offline),
    CONNECTING(R.string.status_connecting),
    ONLINE(R.string.status_online),
    CONFLICT(R.string.status_conflict),
}

sealed interface Screen {
    data object Login : Screen
    data object Unlock : Screen
    data object Vault : Screen
}

data class Item(
    val id: String,
    val pending: Boolean,
    val fields: Map<String, String>,
    val tags: List<String>,
    val deleted: Boolean,
    val history: List<String>,
) {
    fun field(name: String) = fields[name].orEmpty()

    val favorite get() = FAVORITE in tags
    val userTags get() = tags.filterNot { it.startsWith("@") }
    val urls get() = (listOf(field("url")) + field("urls").lines()).map(String::trim).filter(String::isNotEmpty)

    companion object {
        fun parse(recordsJson: String): List<Item> {
            val array = JSONArray(recordsJson)
            return (0 until array.length()).map { i ->
                val entry = array.getJSONObject(i)
                val record = entry.getJSONObject("record")
                val fields = record.getJSONObject("fields")
                val tags = record.getJSONObject("tags")
                val history = record.getJSONArray("history")
                Item(
                    id = entry.getString("id"),
                    pending = entry.getBoolean("pending"),
                    fields = fields.keys().asSequence().associateWith { fields.getJSONObject(it).getString("value") },
                    tags = tags.keys().asSequence().filter { tags.getJSONObject(it).getBoolean("value") }.toList(),
                    deleted = record.getJSONObject("deleted").getBoolean("value"),
                    history = (0 until history.length()).map { history.getJSONObject(it).getString("value") },
                )
            }
        }
    }
}
