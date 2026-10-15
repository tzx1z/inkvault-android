package dev.inkov.inkvault.android

import dev.inkov.inkvault.pep.NS_VAULT
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.XmlEnvironment
import org.jivesoftware.smack.provider.ExtensionElementProvider
import org.jivesoftware.smack.util.XmlStringBuilder
import org.jivesoftware.smack.xml.XmlPullParser
import org.jxmpp.JxmppContext

/** Элемент записи хранилища в узле PEP: версия ключа и шифротекст в base64. */
class EntryElement(val keyVersion: Int, val ciphertext: String) : ExtensionElement {
    override fun getElementName() = "entry"

    override fun getNamespace() = NS_VAULT

    override fun toXML(environment: XmlEnvironment?): CharSequence =
        XmlStringBuilder(this, environment)
            .attribute("v", "1")
            .attribute("key", keyVersion.toString())
            .rightAngleBracket()
            .escape(ciphertext)
            .closeElement(this)
}

class EntryProvider : ExtensionElementProvider<EntryElement>() {
    override fun parse(
        parser: XmlPullParser,
        initialDepth: Int,
        xmlEnvironment: XmlEnvironment?,
        jxmppContext: JxmppContext?,
    ): EntryElement {
        val key = parser.getAttributeValue("key") ?: error("в entry нет атрибута key")
        return EntryElement(key.toInt(), parser.nextText().trim())
    }
}
