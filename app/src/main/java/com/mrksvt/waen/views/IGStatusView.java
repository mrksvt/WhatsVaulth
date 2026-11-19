package com.mrksvt.waen.views;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mrksvt.waen.adapter.IGStatusAdapter;
import com.mrksvt.waen.xposed.utils.Utils;

import org.xmlpull.v1.XmlPullParser;

import java.util.List;

public class IGStatusView extends FrameLayout {
    public final RecyclerView mStatusListView;

    public IGStatusAdapter mStatusAdapter;
    private int mFragmentId;

    public IGStatusView(@NonNull Context context) {
        super(context);
        mStatusListView = createRecyclerView(context);
        init(context);
    }

    /**
     * Android 16 meng-cast AttributeSet menjadi XmlBlock.Parser saat View dibuat.
     * Karena itu AttributeSet buatan tangan tidak cukup. Ambil parser nyata dari XML
     * resource modul, maju sampai tag START_TAG, lalu gunakan sebagai attrs untuk
     * RecyclerView dengan defStyleAttr 0. Tidak ada LayoutManager dari theme WhatsApp
     * yang ikut terbaca; LayoutManager horizontal dipasang sendiri di init().
     */
    private static RecyclerView createRecyclerView(Context context) {
        Context moduleContext = Utils.getModuleContextOrNull();
        if (moduleContext == null) {
            throw new IllegalStateException("WhatsVault module context unavailable");
        }
        int layoutId = moduleContext.getResources().getIdentifier(
                "ig_status_recycler_attrs", "layout", moduleContext.getPackageName());
        if (layoutId == 0) {
            throw new IllegalStateException("IG Status RecyclerView attrs resource not found");
        }

        try (XmlResourceParser parser = moduleContext.getResources().getLayout(layoutId)) {
            int type;
            do {
                type = parser.next();
            } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT);
            if (type != XmlPullParser.START_TAG) {
                throw new IllegalStateException("IG Status RecyclerView attrs has no start tag");
            }
            AttributeSet attrs = Xml.asAttributeSet(parser);
            // Context konstruktor harus berasal dari modul, sama dengan tabel resource
            // parser. Kalau memakai context WhatsApp, id atribut XML modul ditafsirkan
            // lewat tabel resource WhatsApp dan layout_width bisa terbaca sebagai
            // layoutManager="#ffebedf0".
            return new RecyclerView(moduleContext, attrs, 0);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create IG Status RecyclerView", e);
        }
    }

    public int getFragmentId() {
        return mFragmentId;
    }

    public void setFragmentId(int id) {
        mFragmentId = id;
    }

    private void init(Context context) {
        mStatusListView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        mStatusListView.setHasFixedSize(true);
        var animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setAddDuration(200);
        animator.setRemoveDuration(200);
        animator.setMoveDuration(250);
        mStatusListView.setItemAnimator(animator);
        var layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mStatusListView.setLayoutParams(layoutParams);
        addView(mStatusListView);
    }

    @Override
    public void setTranslationY(float f) {
        if (this.getHeight() > 0) {
            int v = f > ((float) this.getHeight()) ? GONE : VISIBLE;
            if (v == VISIBLE) {
                super.setTranslationY(f);
            }
            this.setVisibility(v);
        }
    }

    public void updateList(List<?> newItems) {
        post(() -> {
            if (mStatusAdapter == null) return;
            mStatusAdapter.submitList(newItems);
        });
    }

    public void setAdapter(IGStatusAdapter mStatusAdapter) {
        this.mStatusAdapter = mStatusAdapter;
        mStatusListView.setAdapter(mStatusAdapter);
    }

    public IGStatusAdapter getAdapter() {
        return mStatusAdapter;
    }
}
