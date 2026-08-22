package com.mrksvt.waen.utils

import org.json.JSONObject
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

object FouadThemeConverter {

    private val COLOR_MAP = mapOf(
        "ConvoBack" to "background_color",
        "ModChatColor" to "background_color",
        "ModChaSendColor" to "bubble_right",
        "ModChaSendBKColor" to "bubble_left",
        "HomeText" to "text_color",
        "nameColor" to "text_color",
        "Header_atas" to "primary_color",
        "navigasi" to "primary_color",
        "BGColor" to "background_color",
        "ModConBackColor" to "background_color",
        "ModChatBubbleTextLeft" to "text_color",
        "ModHomeMentionIconColor" to "primary_color",
        "HomeCounterText" to "text_color",
        "HomeBarText" to "text_color",
        "tittle_color" to "primary_color",
        "statuses_bar_bg_picker" to "primary_color",
        "header_colors" to "primary_color"
    )

    fun convert(xmlFile: File, outputCss: File, themeName: String): Boolean {
        return try {
            val colors = LinkedHashMap<String, Int>()
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = db.parse(xmlFile)
            val entries = doc.documentElement.childNodes

            for (i in 0 until entries.length) {
                val node = entries.item(i)
                if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
                val name = node.attributes.getNamedItem("name")?.textContent ?: continue
                val value = node.attributes.getNamedItem("value")?.textContent ?: continue

                val mapped = COLOR_MAP[name]
                if (mapped != null && !colors.containsKey(mapped)) {
                    val intVal = value.toLongOrNull() ?: continue
                    colors[mapped] = intVal.toInt()
                }
            }

            val primary = colors["primary_color"] ?: 0xFF00A884.toInt()
            val background = colors["background_color"] ?: 0xFFECE5DD.toInt()
            val text = colors["text_color"] ?: 0xFF111B21.toInt()

            // Luminance check: text harus kontras dengan background
            val bgLum = luminance(background)
            val textLum = luminance(text)
            val resolvedText = if (kotlin.math.abs(bgLum - textLum) < 0.4) {
                if (bgLum > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            } else text

            val css = buildString {
                appendLine("/*")
                appendLine("primary_color = ${toHex(primary)}")
                appendLine("background_color = ${toHex(background)}")
                appendLine("text_color = ${toHex(resolvedText)}")
                colors["bubble_right"]?.let { appendLine("bubble_right = ${toHex(it)}") }
                colors["bubble_left"]?.let { appendLine("bubble_left = ${toHex(it)}") }
                appendLine("change_colors = true")
                appendLine("*/")
            }
            outputCss.writeText(css)

            // Generate JSON prefs map (format tema WAEnhancer)
            val json = JSONObject()
            json.put("changecolor", jsonEntry("Boolean", true))
            json.put("custom_filters", jsonEntry("Boolean", true))
            json.put("primary_color", jsonEntry("Integer", primary))
            json.put("text_color", jsonEntry("Integer", resolvedText))
            json.put("background_color", jsonEntry("Integer", background))
            colors["bubble_right"]?.let { json.put("bubble_right", jsonEntry("Integer", it)) }
            colors["bubble_left"]?.let { json.put("bubble_left", jsonEntry("Integer", it)) }
            json.put("folder_theme", jsonEntry("String", themeName))
            json.put("custom_css", jsonEntry("String", css))
            json.put("change_colors", jsonEntry("Boolean", true))

            val jsonFile = File(outputCss.parentFile, "$themeName.json")
            jsonFile.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun jsonEntry(type: String, value: Any): JSONObject {
        return JSONObject().put("type", type).put("value", value)
    }

    private fun toHex(argb: Int): String {
        return "#%06X".format(argb.toLong() and 0x00FFFFFFL)
    }

    private fun luminance(color: Int): Double {
        val r = android.graphics.Color.red(color) / 255.0
        val g = android.graphics.Color.green(color) / 255.0
        val b = android.graphics.Color.blue(color) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
