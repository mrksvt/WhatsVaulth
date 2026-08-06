package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.SectionIndexer
import android.widget.TextView
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.utils.Utils
import de.robv.android.xposed.XposedHelpers

class TranslatorWrapperAdapter(
    private val realAdapter: ListAdapter,
    private val prefs: SharedPreferences
) : BaseAdapter(), SectionIndexer {

    companion object {
        private var instance: TranslatorWrapperAdapter? = null

        fun showTranslation(messageId: String, text: String) {
            instance?.let { adapter ->
                adapter.translationMap[messageId] = text
                adapter.notifyDataSetChanged()
            }
        }

        fun hideTranslation(messageId: String) {
            instance?.let { adapter ->
                adapter.translationMap.remove(messageId)
                adapter.notifyDataSetChanged()
            }
        }

        fun hasTranslation(messageId: String): Boolean {
            return instance?.translationMap?.containsKey(messageId) == true
        }
    }

    private val translationMap = HashMap<String, String>()

    init {
        instance = this
    }

    private fun getMessageId(realPos: Int): String? {
        return try {
            val raw = realAdapter.getItem(realPos) ?: return null
            FMessageWpp(raw).key.messageID
        } catch (e: Exception) {
            null
        }
    }

    private fun isFromMe(realPos: Int): Boolean {
        return try {
            val raw = realAdapter.getItem(realPos) ?: return false
            FMessageWpp(raw).key.isFromMe
        } catch (e: Exception) {
            false
        }
    }

    private fun isTextMessage(realPos: Int): Boolean {
        return try {
            val raw = realAdapter.getItem(realPos) ?: return false
            FMessageWpp(raw).messageStr?.isNotBlank() == true
        } catch (e: Exception) {
            false
        }
    }

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }

    override fun getCount(): Int = realAdapter.count * 2

    override fun getItem(pos: Int): Any? =
        if (pos % 2 == 0) realAdapter.getItem(pos / 2) else null

    override fun getItemId(pos: Int): Long =
        if (pos % 2 == 0) realAdapter.getItemId(pos / 2)
        else Long.MAX_VALUE - (pos / 2).toLong()

    override fun hasStableIds(): Boolean = realAdapter.hasStableIds()

    override fun getViewTypeCount(): Int = realAdapter.viewTypeCount

    override fun getItemViewType(pos: Int): Int =
        if (pos % 2 == 0) realAdapter.getItemViewType(pos / 2)
        else android.widget.Adapter.IGNORE_ITEM_VIEW_TYPE

    override fun isEnabled(pos: Int): Boolean =
        if (pos % 2 == 0) realAdapter.isEnabled(pos / 2) else false

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        if (pos % 2 == 0) {
            return realAdapter.getView(pos / 2, convertView, parent)
        }

        val realPos = pos / 2
        val context = parent.context
        val messageId = getMessageId(realPos)
        val translation = messageId?.let { translationMap[it] }

        if (translation.isNullOrBlank() || !isTextMessage(realPos)) {
            return View(context).apply {
                layoutParams = ViewGroup.LayoutParams(0, 0)
                visibility = View.GONE
            }
        }

        val fromMe = isFromMe(realPos)
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)
        val dp12 = Utils.dipToPixels(12)

        val rawBubbleColor = if (fromMe) prefs.getInt("bubble_right", 0)
                             else prefs.getInt("bubble_left", 0)

        val bubbleBgColor: Int
        val bubbleTextColor: Int
        if (rawBubbleColor != 0) {
            val hsv = FloatArray(3)
            Color.colorToHSV(rawBubbleColor, hsv)
            hsv[2] = (hsv[2] * 0.75f).coerceIn(0.1f, 1.0f)
            bubbleBgColor = Color.HSVToColor(hsv)
            bubbleTextColor = if (hsv[2] < 0.5f) Color.WHITE else Color.BLACK
        } else {
            if (fromMe) {
                bubbleBgColor = Color.parseColor("#1A237E")
                bubbleTextColor = Color.parseColor("#E8EAF6")
            } else {
                bubbleBgColor = Color.parseColor("#1B5E20")
                bubbleTextColor = Color.parseColor("#E8F5E9")
            }
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bubbleBgColor)
            cornerRadius = dp12.toFloat()
        }

        val gravity = if (fromMe) Gravity.END else Gravity.START

        val bubbleResId = if (fromMe)
            Utils.getIDFromModule("groq_translator_bubble_outgoing")
        else
            Utils.getIDFromModule("groq_translator_bubble_incoming")

        val tv = TextView(context).apply {
            if (bubbleResId > 0) id = bubbleResId
            text = "🌐 $translation"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(bubbleTextColor)
            background = bgDrawable
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(dp8, dp4, dp8, dp4)
        }

        val tvLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.gravity = gravity }
        tv.layoutParams = tvLp

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8, dp4, dp8, dp4)
            addView(tv)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun getSections(): Array<Any> =
        (realAdapter as? SectionIndexer)?.sections ?: emptyArray()

    override fun getPositionForSection(sectionIndex: Int): Int {
        val realPos = (realAdapter as? SectionIndexer)?.getPositionForSection(sectionIndex) ?: 0
        return realPos * 2
    }

    override fun getSectionForPosition(position: Int): Int =
        (realAdapter as? SectionIndexer)?.getSectionForPosition(position / 2) ?: 0
}
