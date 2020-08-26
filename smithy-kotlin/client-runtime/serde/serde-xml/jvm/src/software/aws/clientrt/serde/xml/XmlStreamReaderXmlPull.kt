/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

private class XmlStreamReaderXmlPull(
    payload: ByteArray,
    charset: Charset = Charsets.UTF_8,
    private val parser: XmlPullParser = xmlPullParserFactory()
) : XmlStreamReader {

    private var peekedToken: XmlToken? = null

    init {
        parser.setInput(ByteArrayInputStream(payload), charset.toString())
    }

    companion object {
        private fun xmlPullParserFactory(): XmlPullParser {
            val factory = XmlPullParserFactory.newInstance("org.xmlpull.mxp1.MXParser", null)
            return factory.newPullParser()
        }
    }

    // NOTE: Because of the way peeking is managed, any code in this class wanting to get the next token must call
    // this method rather than calling `parser.nextToken()` directly.
    override fun nextToken(): XmlToken {
        if (peekedToken != null) {
            val rv = peekedToken!!
            peekedToken = null
            return rv
        }

        try {
            return when (val nt = parser.nextToken()) {
                XmlPullParser.START_DOCUMENT -> nextToken()
                XmlPullParser.END_DOCUMENT -> XmlToken.EndDocument
                XmlPullParser.START_TAG -> XmlToken.BeginElement(parser.qualifiedName(), parseAttributes())
                XmlPullParser.END_TAG -> XmlToken.EndElement(parser.qualifiedName())
                XmlPullParser.CDSECT,
                XmlPullParser.COMMENT,
                XmlPullParser.DOCDECL,
                XmlPullParser.IGNORABLE_WHITESPACE -> nextToken()
                XmlPullParser.TEXT -> XmlToken.Text(parser.text.blankToNull())
                else -> throw IllegalStateException("Unhandled tag $nt")
            }
        } catch (e: Exception) {
            throw XmlGenerationException(e)
        }
    }

    // Create qualified name from current node
    private fun XmlPullParser.qualifiedName(): XmlToken.QualifiedName =
        XmlToken.QualifiedName(name, namespace.blankToNull())

    // Return attribute map from attributes of current node
    private fun parseAttributes(): Map<XmlToken.QualifiedName, String> {
        if (parser.attributeCount == 0) return emptyMap()

        return (0 until parser.attributeCount)
            .asSequence()
            .map { attributeIndex ->
                XmlToken.QualifiedName(
                    parser.getAttributeName(attributeIndex),
                    parser.getAttributeNamespace(attributeIndex).blankToNull()
                ) to parser.getAttributeValue(attributeIndex)
            }
            .toMap()
    }

    //This does one of three things:
    // 1: if the next token is BeginElement, then that node is skipped
    // 2: if the next token is Text or EndElement, read tokens until the end of the current node is exited
    // 3: if the next token is EndDocument, NOP
    override fun skipNext() {
        val startDepth = parser.depth
        var nt = peek()

        when (nt) {
            // Code path 1
            is XmlToken.BeginElement -> {
                val currentNodeName = nt.name

                var st = nextToken()
                while (st != XmlToken.EndDocument) {
                    if (st is XmlToken.EndElement && parser.depth == startDepth && st.name.name == currentNodeName.name && st.name.namespace == currentNodeName.namespace) return
                    st = nextToken()
                    require(parser.depth >= startDepth) { "Traversal depth ${parser.depth} exceeded start node depth $startDepth" }
                }
            }
            is XmlToken.EndDocument -> return
            // Code path 2
            else -> {
                while (nt != XmlToken.EndDocument) {
                    nt = nextToken()
                    if (nt is XmlToken.EndElement && parser.depth == startDepth) return
                }
            }
        }
    }

    override fun peek(): XmlToken = when (peekedToken) {
        null -> {
            peekedToken = nextToken()
            peekedToken as XmlToken
        }
        else -> peekedToken as XmlToken
    }

    override fun currentDepth(): Int = parser.depth
}

private fun String?.blankToNull(): String? = if (this?.isBlank() != false) null else this

/*
* Creates a [JsonStreamReader] instance
*/
internal actual fun xmlStreamReader(payload: ByteArray): XmlStreamReader = XmlStreamReaderXmlPull(payload)