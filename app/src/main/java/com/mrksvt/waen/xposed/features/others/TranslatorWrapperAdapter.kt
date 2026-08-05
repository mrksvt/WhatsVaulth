package com.mrksvt.waen.xposed.features.others

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
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
import com.mrksvt.waen.xposed.core.components.FMessageWpp
import com.mrksvt.waen.xposed.utils.Utils

class TranslatorWrapperAdapter(
    private val realAdapter: ListAdapter,
    private val prefs: SharedPreferences
) : BaseAdapter(), SectionIndexer {

    companion object {
        const val TAG_PLACEHOLDER = "groq_placeholder_view_type"
    }

    private val isFromMeCache = SparseArray<Boolean>()
    private val hasTextCache = SparseArray<Boolean>()

    private fun isTextMessage(realPos: Int): Boolean {
        hasTextCache[realPos]?.let { return it }
        val result = try {
            val raw = realAdapter.getItem(realPos)
            if (raw != null) FMessageWpp(raw).messageStr?.isNotBlank() == true else false
        } catch (e: Exception) {
            false
        }
        hasTextCache.put(realPos, result)
        return result
    }

    private fun isFromMe(realPos: Int): Boolean {
        isFromMeCache[realPos]?.let { return it }
        val result = try {
            val raw = realAdapter.getItem(realPos)
            if (raw != null) FMessageWpp(raw).key.isFromMe else false
        } catch (e: Exception) {
            false
        }
        isFromMeCache.put(realPos, result)
        return result
    }

    override fun notifyDataSetChanged() {
        isFromMeCache.clear()
        hasTextCache.clear()
        super.notifyDataSetChanged()
    }

    override fun getCount(): Int = realAdapter.count * 2

    override fun getItem(pos: Int): Any? {
        return if (pos % 2 == 0) realAdapter.getItem(pos / 2) else null
    }

    override fun getItemId(pos: Int): Long {
        return if (pos % 2 == 0) {
            realAdapter.getItemId(pos / 2)
        } else {
            Long.MAX_VALUE - (pos / 2).toLong()
        }
    }

    override fun hasStableIds(): Boolean = realAdapter.hasStableIds()

    override fun getViewTypeCount(): Int = realAdapter.viewTypeCount + 1

    override fun getItemViewType(pos: Int): Int {
        return if (pos % 2 == 0) {
            realAdapter.getItemViewType(pos / 2)
        } else {
            realAdapter.viewTypeCount
        }
    }

    override fun isEnabled(pos: Int): Boolean {
        return if (pos % 2 == 0) realAdapter.isEnabled(pos / 2) else false
    }

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        return if (pos % 2 == 0) {
            val safeConvert = if (convertView?.tag == TAG_PLACEHOLDER) null else convertView
            realAdapter.getView(pos / 2, safeConvert, parent)
        } else {
            val realPos = pos / 2
            if (!isTextMessage(realPos)) {
                val empty = View(parent.context)
                empty.layoutParams = ViewGroup.LayoutParams(0, 0)
                return empty
            }
            createOrRecyclePlaceholderView(convertView, parent, isFromMe(realPos))
        }
    }

    private fun createOrRecyclePlaceholderView(
        convertView: View?,
        parent: ViewGroup,
        isFromMe: Boolean
    ): View {
        val context = parent.context
        val dp8 = Utils.dipToPixels(8)
        val dp4 = Utils.dipToPixels(4)
        val dp2 = Utils.dipToPixels(2)
        val dp16 = Utils.dipToPixels(16)

        val bgColor = if (isFromMe) "#1A237E" else "#1B5E20"
        val textColor = if (isFromMe) "#E8EAF6" else "#E8F5E9"
        val gravity = if (isFromMe) Gravity.END else Gravity.START

        if (convertView is LinearLayout && convertView.tag == TAG_PLACEHOLDER) {
            val tv = convertView.getChildAt(0) as? TextView
            if (tv != null) {
                tv.setBackgroundColor(Color.parseColor(bgColor))
                tv.setTextColor(Color.parseColor(textColor))
            }
            convertView.gravity = gravity
            return convertView
        }

        // Create new
        val root = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp16, dp2, dp16, dp2)
            this.gravity = gravity
            tag = TAG_PLACEHOLDER
        }

        val tv = TextView(context).apply {
            text = "[Translation Placeholder]"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor(textColor))
            setBackgroundColor(Color.parseColor(bgColor))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setPadding(dp8, dp4, dp8, dp4)
        }

        root.addView(tv)
        return root
    }

    // SectionIndexer delegation — remap positions with *2

    override fun getSections(): Array<Any> {
        return (realAdapter as? SectionIndexer)?.sections ?: emptyArray()
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        val realPos = (realAdapter as? SectionIndexer)?.getPositionForSection(sectionIndex) ?: 0
        return realPos * 2
    }

    override fun getSectionForPosition(position: Int): Int {
        return (realAdapter as? SectionIndexer)?.getSectionForPosition(position / 2) ?: 0
    }
}
