package io.github.peshke.launchio;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * The rows under the dock. As tall as its rows, even when that is taller than the screen: a plain
 * wrap_content child of the grid's frame would be cut to the frame's height, and the last rows with it.
 */
public class RowsPanel extends LinearLayout {

    public RowsPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
}
