package com.mrksvt.waen.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;

public final class MainSidebarController {

    private enum State { CLOSED, OPENING, OPEN, CLOSING }

    private static final long DURATION_MS = 280;
    private final View overlay;
    private final View panel;
    private final View scrim;
    private final View content;
    private final Runnable onStateChanged;
    private final int contentAccessibility;
    private final Runnable beginOpening = this::tryStartOpening;
    private final View.OnLayoutChangeListener layoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> tryStartOpening();

    private State state = State.CLOSED;
    private boolean openRequested;
    private boolean disposed;
    private ValueAnimator animator;
    private View previousFocus;

    public MainSidebarController(View overlay, View panel, View scrim, View content,
                                 Runnable onStateChanged) {
        this.overlay = overlay;
        this.panel = panel;
        this.scrim = scrim;
        this.content = content;
        this.onStateChanged = onStateChanged;
        contentAccessibility = content.getImportantForAccessibility();
        overlay.addOnLayoutChangeListener(layoutListener);
        scrim.setOnClickListener(view -> close());
    }

    public boolean isShowing() {
        return state != State.CLOSED;
    }

    public boolean isOpenRequested() {
        return openRequested;
    }

    public void toggle() {
        if (disposed) return;
        openRequested = !openRequested;
        reconcile();
        onStateChanged.run();
    }

    public void close() {
        if (disposed) return;
        openRequested = false;
        reconcile();
        onStateChanged.run();
    }

    /** Closes without animating, so the destination page is revealed on the same frame. */
    public void closeImmediately() {
        if (disposed) return;
        reset();
        onStateChanged.run();
    }

    private void reconcile() {
        if (state == State.CLOSED && openRequested) {
            state = State.OPENING;
            previousFocus = overlay.getRootView().findFocus();
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            panel.setTranslationX(-panelWidth());
            scrim.setAlpha(0f);
            overlay.setVisibility(View.VISIBLE);
            overlay.post(beginOpening);
        } else if (state == State.OPEN && !openRequested) {
            state = State.CLOSING;
            animate(false);
        }
        // A request during animation only changes the desired final state.
    }

    private int panelWidth() {
        return panel.getWidth() > 0 ? panel.getWidth() : Math.max(0, panel.getLayoutParams().width);
    }

    private void tryStartOpening() {
        if (disposed || state != State.OPENING || animator != null) return;
        if (!openRequested) {
            finishClosed();
            return;
        }
        if (panel.getWidth() > 0 && overlay.getWidth() > 0) {
            panel.setTranslationX(-panel.getWidth());
            animate(true);
        }
    }

    private void animate(boolean opening) {
        float start = panel.getTranslationX();
        float end = opening ? 0f : Math.max(overlay.getWidth(), panelWidth());
        ValueAnimator next = ValueAnimator.ofFloat(start, end);
        animator = next;
        next.setDuration(ValueAnimator.areAnimatorsEnabled() ? DURATION_MS : 0);
        next.addUpdateListener(value -> {
            panel.setTranslationX((float) value.getAnimatedValue());
            float fraction = value.getAnimatedFraction();
            scrim.setAlpha(opening ? fraction : 1f - fraction);
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator != animation || disposed) return;
                animator = null;
                if (opening) {
                    state = State.OPEN;
                    scrim.setAlpha(1f);
                    panel.requestFocus();
                } else {
                    finishClosed();
                }
                reconcile();
                onStateChanged.run();
            }
        });
        next.start();
    }

    private void finishClosed() {
        // Hide before resetting to the left, so the reset never appears onscreen.
        overlay.setVisibility(View.GONE);
        panel.setTranslationX(-panelWidth());
        scrim.setAlpha(0f);
        state = State.CLOSED;
        content.setImportantForAccessibility(contentAccessibility);
        if (previousFocus != null && previousFocus.isAttachedToWindow()) {
            previousFocus.requestFocus();
        }
        previousFocus = null;
        onStateChanged.run();
    }

    public void restoreOpen() {
        if (disposed) return;
        reset();
        openRequested = true;
        state = State.OPEN;
        previousFocus = overlay.getRootView().findFocus();
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.setTranslationX(0f);
        scrim.setAlpha(1f);
        overlay.setVisibility(View.VISIBLE);
        onStateChanged.run();
    }

    public void reset() {
        overlay.removeCallbacks(beginOpening);
        openRequested = false;
        ValueAnimator current = animator;
        animator = null;
        if (current != null) current.cancel();
        finishClosed();
    }

    public void dispose() {
        if (disposed) return;
        reset();
        disposed = true;
        overlay.removeOnLayoutChangeListener(layoutListener);
        scrim.setOnClickListener(null);
    }
}
