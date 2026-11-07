package com.mrksvt.waen.views;

import android.content.Context;
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
        mStatusListView = new RecyclerView(context);
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
//        layoutParams.setMargins(Utils.dipToPixels(4), Utils.dipToPixels(10), 0, 0);
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
