package com.mrksvt.waen.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mrksvt.waen.adapter.IGStatusAdapter;

import java.util.List;

public class IGStatusView extends FrameLayout {
    public final RecyclerView mStatusListView;

    public IGStatusAdapter mStatusAdapter;
    private int mFragmentId;

    public IGStatusView(@NonNull Context context) {
        super(context);
        // RecyclerView TIDAK dibuat dengan `new RecyclerView(context)`.
        // Konstruktor itu meneruskan attrs null ke createLayoutManager. Di 2.26.46
        // theme WhatsApp menyuplai nama LayoutManager lewat defStyleAttr, pembuatan
        // LayoutManager itu gagal, lalu pesan errornya dibangun dari
        // attrs.getPositionDescription() sehingga WhatsApp crash dengan
        // NullPointerException.
        //
        // Solusinya: AttributeSet non-null yang kosong plus defStyleAttr 0. Dengan
        // begitu tidak ada LayoutManager dari theme yang dicoba dibuat, lalu
        // LayoutManager horizontal dipasang sendiri di init().
        mStatusListView = new RecyclerView(context, EmptyAttributeSet.INSTANCE, 0);
        init(context);
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

    /**
     * AttributeSet kosong. Dipakai supaya RecyclerView tidak membaca LayoutManager dari
     * theme WhatsApp, sekaligus tidak pernah memanggil getPositionDescription() pada
     * attrs null.
     */
    private static final class EmptyAttributeSet implements AttributeSet {
        static final EmptyAttributeSet INSTANCE = new EmptyAttributeSet();

        @Override
        public int getAttributeCount() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public String getAttributeNamespace(int index) {
            return null;
        }

        @Override
        public String getAttributeName(int index) {
            return null;
        }

        @Override
        public String getAttributeValue(int index) {
            return null;
        }

        @Override
        public String getAttributeValue(String namespace, String name) {
            return null;
        }

        @Override
        public String getPositionDescription() {
            return "IGStatusView";
        }

        @Override
        public int getAttributeNameResource(int index) {
            return 0;
        }

        @Override
        public int getAttributeListValue(String namespace, String attribute,
                                         String[] options, int defaultValue) {
            return defaultValue;
        }

        @Override
        public boolean getAttributeBooleanValue(String namespace, String attribute,
                                                boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public int getAttributeResourceValue(String namespace, String attribute,
                                             int defaultValue) {
            return defaultValue;
        }

        @Override
        public int getAttributeIntValue(String namespace, String attribute,
                                        int defaultValue) {
            return defaultValue;
        }

        @Override
        public int getAttributeUnsignedIntValue(String namespace, String attribute,
                                                int defaultValue) {
            return defaultValue;
        }

        @Override
        public float getAttributeFloatValue(String namespace, String attribute,
                                            float defaultValue) {
            return defaultValue;
        }

        @Override
        public int getAttributeListValue(int index, String[] options, int defaultValue) {
            return defaultValue;
        }

        @Override
        public boolean getAttributeBooleanValue(int index, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public int getAttributeResourceValue(int index, int defaultValue) {
            return defaultValue;
        }

        @Override
        public int getAttributeIntValue(int index, int defaultValue) {
            return defaultValue;
        }

        @Override
        public int getAttributeUnsignedIntValue(int index, int defaultValue) {
            return defaultValue;
        }

        @Override
        public float getAttributeFloatValue(int index, float defaultValue) {
            return defaultValue;
        }

        @Override
        public String getIdAttribute() {
            return null;
        }

        @Override
        public String getClassAttribute() {
            return null;
        }

        @Override
        public int getIdAttributeResourceValue(int defaultValue) {
            return defaultValue;
        }

        @Override
        public int getStyleAttribute() {
            return 0;
        }
    }
}
