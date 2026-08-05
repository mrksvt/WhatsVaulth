package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.SectionIndexer
import android.widget.TextView
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.utils.Utils

class TranslatorWrapperAdapter(
    private val realAdapter: ListAdapter,
    private val prefs: SharedPreferences
) : BaseAdapter(), SectionIndexer {

    companion object {
        const val TAG_BUBBLE = "groq_translator_bubble_row"

        private val translations = HashMap<String, String>()
        private var activeAdapter: TranslatorWrapperAdapter? = null

        fun updateTranslation(messageId: String, text: String) {
            translations[messageId] = text
            Handler(Looper.getMainLooper()).post {
                activeAdapter?.notifyDataSetChanged()
            }
        }

        fun removeTranslation(messageId: String) {
            translations.remove(messageId)
            Handler(Looper.getMainLooper()).post {
                activeAdapter?.notifyDataSetChanged()
            }
        }

        fun hasTranslation(messageId: String): Boolean = translations.containsKey(messageId)
    }

    private val messageIdCache = SparseArray<String>()
    private val isFromMeCache = SparseArray<Boolean>()

    init {
        activeAdapter = this
    }

    private fun getMessageId(realPos: Int): String? {
        messageIdCache[realPos]?.let { return it }
        return try {
            val raw = realAdapter.getItem(realPos) ?: return null
            val id = FMessageWpp(raw).key.messageID ?: return null
            messageIdCache.put(realPos, id)
            id
        } catch (e: Exception) { null }
    }

    private fun isFromMe(realPos: Int): Boolean {
        isFromMeCache[realPos]?.let { return it }
        return try {
            val raw = realAdapter.getItem(realPos) ?: return false
            val result = FMessageWpp(raw).key.isFromMe
            isFromMeCache.put(realPos, result)
            result
        } catch (e: Exception) { false }
    }

    override fun notifyDataSetChanged() {
        messageIdCache.clear()
        isFromMeCache.clear()
        super.notifyDataSetChanged()
    }

    override fun getCount(): Int = realAdapter.count * 2

    override fun getItem(pos: Int): Any? = if (pos % 2 == 0) realAdapter.getItem(pos / 2) else null

    override fun getItemId(pos: Int): Long =
        if (pos % 2 == 0) realAdapter.getItemId(pos / 2)
        else Long.MAX_VALUE - (pos / 2).toLong()

    override fun hasStableIds(): Boolean = realAdapter.hasStableIds()

    override fun getViewTypeCount(): Int = realAdapter.viewTypeCount + 1

    override fun getItemViewType(pos: Int): Int =
        if (pos % 2 == 0) realAdapter.getItemViewType(pos / 2)
        else realAdapter.viewTypeCount

    override fun isEnabled(pos: Int): Boolean =
        if (pos % 2 == 0) realAdapter.isEnabled(pos / 2) else false

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        return if (pos % 2 == 0) {
            val safeConvert = if (convertView?.tag == TAG_BUBBLE) null else convertView
            realAdapter.getView(pos / 2, safeConvert, parent)
        } else {
            val realPos = pos / 2
            val messageId = getMessageId(realPos)
            val translatedText = messageId?.let { translations[it] }

            if (translatedText == null) {
                val empty = View(parent.context)
                empty.tag = TAG_BUBBLE
                empty.layoutParams = ViewGroup.LayoutParams(0, 0)
                empty
            } else {
                buildBubbleView(convertView, parent, translatedText, isFromMe(realPos), messageId)
            }
        }
    }

    private fun buildBubbleView(
        convertView: View?,
        parent: ViewGroup,
        text: String,
        isFromMe: Boolean,
        messageId: String
    ): View {
        val context = parent.context
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)
        val dp16 = Utils.dipToPixels(16)

        val bgColor = if (isFromMe) "#1A237E" else "#1B5E20"
        val textColor = if (isFromMe) "#E8EAF6" else "#E8F5E9"
        val gravity = if (isFromMe) Gravity.END else Gravity.START
        val bubbleId = if (isFromMe) R.id.groq_translator_bubble_outgoing
                       else R.id.groq_translator_bubble_incoming

        val root: LinearLayout = if (convertView is LinearLayout && convertView.tag == TAG_BUBBLE) {
            convertView
        } else {
            LinearLayout(context).apply {
                tag = TAG_BUBBLE
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp16, dp4, dp16, dp4)
            }
        }

        root.gravity = gravity
        root.id = bubbleId

        val tv: TextView = if (root.childCount > 0 && root.getChildAt(0) is TextView) {
            root.getChildAt(0) as TextView
        } else {
            root.removeAllViews()
            TextView(context).also { root.addView(it) }
        }

        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = Utils.dipToPixels(8).toFloat()
        }

        tv.apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor(textColor))
            background = bgDrawable
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(dp8, dp4, dp8, dp4)
        }

        return root
    }

    override fun getSections(): Array<Any> =
        (realAdapter as? SectionIndexer)?.sections ?: emptyArray()

    override fun getPositionForSection(sectionIndex: Int): Int =
        ((realAdapter as? SectionIndexer)?.getPositionForSection(sectionIndex) ?: 0) * 2

    override fun getSectionForPosition(position: Int): Int =
        (realAdapter as? SectionIndexer)?.getSectionForPosition(position / 2) ?: 0
}
