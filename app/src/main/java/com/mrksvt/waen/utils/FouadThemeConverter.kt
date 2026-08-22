package com.mrksvt.waen.utils

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

object FouadThemeConverter {

    // Mapping key Fouad -> WAE CSS property
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

    fun convert(xmlFile: File, outputCss: File): Boolean {
        try {
            val props = Properties()
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = db.parse(xmlFile)
            val entries = doc.documentElement.childNodes

            for (i in 0 until entries.length) {
                val node = entries.item(i)
                if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
                val name = node.attributes.getNamedItem("name")?.textContent ?: continue
                val value = node.attributes.getNamedItem("value")?.textContent ?: continue

                val mapped = COLOR_MAP[name]
                if (mapped != null) {
                    val hex = intToHex(value)
                    if (hex != null) {
                        // hanya simpan yang pertama ditemukan (prioritas urutan)
                        if (!props.containsKey(mapped)) {
                            props[mapped] = hex
                        }
                    }
                }
            }

            // Default values jika tidak ada mapping
            if (!props.containsKey("primary_color")) props["primary_color"] = "#00A884"
            if (!props.containsKey("background_color")) props["background_color"] = "#ECE5DD"
            if (!props.containsKey("text_color")) props["text_color"] = "#111B21"

            // Wajib agar CustomThemeV2 memproses warna dari CSS
            props["change_colors"] = "true"

            // Generate CSS comment block
            val css = buildString {
                appendLine("/*")
                for ((key, value) in props) {
                    appendLine("$key = $value")
                }
                appendLine("*/")
            }
            outputCss.writeText(css)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Parse int desimal (bisa negatif) -> hex #RRGGBB
    private fun intToHex(value: String): String? {
        return try {
            val intVal = value.toLong()
            // Konversi signed int ke unsigned ARGB
            val argb = intVal and 0xFFFFFFFFL
            val rgb = argb and 0x00FFFFFFL
            if (rgb == 0L && argb shr 24 == 0xFFL) return null // hitam murni? biarkan
            "#%06X".format(rgb)
        } catch (e: NumberFormatException) {
            null
        }
    }
}