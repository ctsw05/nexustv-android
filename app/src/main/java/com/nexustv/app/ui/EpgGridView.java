package com.nexustv.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import com.nexustv.app.data.Channel;
import com.nexustv.app.data.EpgProgram;
import java.util.List;
import java.util.Map;

/**
 * Custom view that draws ONE ROW of the EPG grid.
 * Used inside a RecyclerView so vertical scrolling is handled natively.
 */
public class EpgGridView extends View {

    public interface CellListener {
        void onCellFocused(Channel channel, EpgProgram program, int progIndex);
        void onCellSelected(Channel channel, EpgProgram program);
    }

    public static final float PX_PER_MIN = 2.0f; // slightly smaller than before
    public static final float ROW_H_DP   = 52f;

    private Channel channel;
    private List<EpgProgram> programs;
    private long windowStartMs;
    private long windowEndMs;
    private int focusCol = -1; // -1 = no focus on this row
    private boolean rowHasFocus = false;

    private float rowHeightPx;
    private float density;
    private CellListener listener;

    private final Paint paintBg      = new Paint();
    private final Paint paintProgram = new Paint();
    private final Paint paintCurrent = new Paint();
    private final Paint paintNowLine = new Paint();
    private final Paint paintText    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSub     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTime    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public EpgGridView(Context ctx) { super(ctx); init(ctx); }
    public EpgGridView(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }
    public EpgGridView(Context ctx, AttributeSet a, int d) { super(ctx, a, d); init(ctx); }

    private void init(Context ctx) {
        density = ctx.getResources().getDisplayMetrics().density;
        rowHeightPx = ROW_H_DP * density;

        paintBg.setColor(0xFF141414);
        paintProgram.setColor(0xFF1C2B38);
        paintCurrent.setColor(0xFF0D2233);
        paintNowLine.setColor(0xFFEF4444);
        paintNowLine.setStrokeWidth(2f * density);
        paintText.setColor(0xFFEEEEEE);
        paintText.setTextSize(12f * density);
        paintSub.setColor(0xFF888888);
        paintSub.setTextSize(10f * density);
        paintTime.setColor(0xFF0EA5E9);
        paintTime.setTextSize(10f * density);
    }

    public void setData(Channel channel, List<EpgProgram> programs, long windowStartMs, long windowEndMs) {
        this.channel = channel;
        this.programs = programs;
        this.windowStartMs = windowStartMs;
        this.windowEndMs = windowEndMs;
        invalidate();
    }

    public void setFocusCol(int col, boolean hasFocus) {
        this.focusCol = col;
        this.rowHasFocus = hasFocus;
        invalidate();
    }

    public void setListener(CellListener l) { listener = l; }

    public int getFocusCol() { return focusCol; }

    public int getProgramCount() { return programs != null ? programs.size() : 0; }

    /** Find program index that contains the given time */
    public int getProgramIndexAtTime(long ms) {
        if (programs == null) return 0;
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).startMs <= ms && programs.get(i).stopMs > ms) return i;
        }
        // Find first program after ms
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).startMs >= ms) return i;
        }
        return 0;
    }

    public float getXForProgramIndex(int idx) {
        if (programs == null || idx >= programs.size()) return 0;
        return msToX(programs.get(idx).startMs);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int totalMins = (int)((windowEndMs - windowStartMs) / 60000);
        int w = (int)(totalMins * PX_PER_MIN * density);
        int h = (int) rowHeightPx;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paintBg);
        if (programs == null) return;

        long now = System.currentTimeMillis();
        float pad = 2 * density;
        float corner = 5 * density;

        for (int pi = 0; pi < programs.size(); pi++) {
            EpgProgram p = programs.get(pi);
            if (p.stopMs < windowStartMs || p.startMs > windowEndMs) continue;

            float left  = msToX(Math.max(p.startMs, windowStartMs));
            float right = msToX(Math.min(p.stopMs, windowEndMs));
            if (right - left < pad * 2) continue;

            boolean isCurrent = p.startMs <= now && p.stopMs > now;
            boolean isFocused = rowHasFocus && pi == focusCol;

            rect.set(left + pad, pad, right - pad, rowHeightPx - pad);

            // Background
            if (isFocused) {
                paintProgram.setColor(0xFF1A4A6A);
                canvas.drawRoundRect(rect, corner, corner, paintProgram);
                // Blue focus border
                Paint border = new Paint();
                border.setColor(0xFF0EA5E9);
                border.setStyle(Paint.Style.STROKE);
                border.setStrokeWidth(2f * density);
                canvas.drawRoundRect(rect, corner, corner, border);
                paintProgram.setColor(0xFF1C2B38);
            } else if (isCurrent) {
                paintProgram.setColor(0xFF0D2233);
                canvas.drawRoundRect(rect, corner, corner, paintProgram);
                // Progress underline
                float prog = p.getProgress();
                canvas.drawRect(rect.left, rect.bottom - 2 * density,
                    rect.left + (rect.right - rect.left) * prog, rect.bottom, paintNowLine);
                paintProgram.setColor(0xFF1C2B38);
            } else {
                canvas.drawRoundRect(rect, corner, corner, paintProgram);
            }

            // Clip text
            canvas.save();
            canvas.clipRect(rect.left + 6 * density, 0, rect.right - 4 * density, rowHeightPx);
            float tx = rect.left + 8 * density;

            // Title
            paintText.setColor(isFocused ? 0xFFFFFFFF : isCurrent ? 0xFFEEEEEE : 0xFFAAAAAA);
            canvas.drawText(p.title, tx, rowHeightPx * 0.44f, paintText);

            // Time
            paintTime.setColor(isFocused ? 0xFF0EA5E9 : 0xFF446688);
            canvas.drawText(EpgProgram.formatTime(p.startMs), tx, rowHeightPx * 0.72f, paintTime);

            canvas.restore();
        }

        // "Now" red line
        float nowX = msToX(now);
        if (nowX > 0 && nowX < getWidth()) {
            canvas.drawLine(nowX, 0, nowX, rowHeightPx, paintNowLine);
        }

        // Row bottom divider
        Paint div = new Paint();
        div.setColor(0xFF1A2530);
        canvas.drawLine(0, rowHeightPx - 1, getWidth(), rowHeightPx - 1, div);
    }

    private float msToX(long ms) {
        return (ms - windowStartMs) / 60000f * PX_PER_MIN * density;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (programs == null || programs.isEmpty()) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (focusCol > 0) {
                focusCol--;
                if (listener != null) listener.onCellFocused(channel, programs.get(focusCol), focusCol);
                invalidate();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (focusCol < programs.size() - 1) {
                focusCol++;
                if (listener != null) listener.onCellFocused(channel, programs.get(focusCol), focusCol);
                invalidate();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (listener != null && focusCol >= 0 && focusCol < programs.size()) {
                listener.onCellSelected(channel, programs.get(focusCol));
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
