package com.nexustv.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

/** Horizontal scroll view that notifies a listener when scrolled (for syncing the channel column). */
public class EpgScrollView extends HorizontalScrollView {

    public interface ScrollListener { void onScrollChanged(int x); }
    private ScrollListener scrollListener;

    public EpgScrollView(Context ctx) { super(ctx); }
    public EpgScrollView(Context ctx, AttributeSet attrs) { super(ctx, attrs); }
    public EpgScrollView(Context ctx, AttributeSet attrs, int defStyle) { super(ctx, attrs, defStyle); }

    public void setScrollListener(ScrollListener l) { scrollListener = l; }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (scrollListener != null) scrollListener.onScrollChanged(x);
    }
}
