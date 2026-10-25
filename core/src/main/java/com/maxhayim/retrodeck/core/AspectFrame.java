package com.maxhayim.retrodeck.core;

import android.content.Context;
import android.widget.FrameLayout;

/** A frame kept at a fixed aspect ratio inside whatever space it gets, like a head unit's screen. */
public class AspectFrame extends FrameLayout {

    private final float ratio;

    /** ratio = width / height; 1024 / 600 for a typical head unit. */
    public AspectFrame(Context context, float ratio) {
        super(context);
        this.ratio = ratio;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED || w / ratio <= h) h = Math.round(w / ratio);
        else w = Math.round(h * ratio);
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }
}
