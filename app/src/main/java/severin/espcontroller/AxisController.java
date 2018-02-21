package severin.espcontroller;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * Created by Severin on 14.12.2017.
 * Basically a SeekBar, just easier to handle
 */

public class AxisController extends View {


    private int width, height;
    private Orientation orientation;
    private int minValue, maxValue, defaultValue, value;
    //boolean if the cursor should be reset to the default Value after releasing it
    private boolean resetOnRelease;
    //Thickness of cursor
    private int lineThickness = 20;
    //Position of Cursor
    private int curPos;

    private ArrayList<AxisControllerListener> listeners = new ArrayList<AxisControllerListener>();

    private int colorOval, colorLine;

    public AxisController(Context context, AttributeSet attrSet) {
        super(context, attrSet);
        TypedArray a = context.obtainStyledAttributes(attrSet, R.styleable.AxisController,0 ,0);
        this.width = a.getInteger(R.styleable.AxisController_controllerWidth, 100);
        this.height = a.getInteger(R.styleable.AxisController_controllerHeight, 100);
        this.minValue = a.getInteger(R.styleable.AxisController_minValue, -100);
        this.maxValue = a.getInteger(R.styleable.AxisController_maxValue, 100);
        this.defaultValue = a.getInteger(R.styleable.AxisController_defaultValue, 0);
        this.value = defaultValue;
        this.orientation = a.getInteger(R.styleable.AxisController_Orientation, 0) == 1 ? Orientation.VERTICAL : Orientation.HORIZONTAL;
        this.resetOnRelease = a.getBoolean(R.styleable.AxisController_resetOnRelease, false);

        a.recycle();

        //set Colors
        colorOval = getResources().getColor(R.color.controllerOval, context.getTheme());
        colorLine = getResources().getColor(R.color.controllerLine, context.getTheme());

        resetCursor();
    }


    @Override
    public void onDraw(Canvas canvas) {
        Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pFill.setStyle(Paint.Style.FILL_AND_STROKE);

        pFill.setColor(colorOval);

        canvas.drawOval(0, 0, this.width, this.height, pFill);

        pFill.setColor(0xFFFFFFFF);
        int x1, y1, x2, y2;
        int halfLineThickness = lineThickness / 2;
        if (orientation == Orientation.HORIZONTAL) {
            x1 = Math.max(Math.min(curPos - halfLineThickness, width - lineThickness), 0);
            x2 = x1 + lineThickness;
            y1 = 0;
            y2 = height;
            canvas.drawLine(width / 2, 0, width / 2, height, pFill);
        } else {
            y1 = Math.max(Math.min(curPos - halfLineThickness, height - lineThickness), 0);
            y2 = y1 + lineThickness;
            x1 = 0;
            x2 = width;
            canvas.drawLine(0, height / 2, width, height / 2, pFill);
        }

        pFill.setColor(colorLine);
        canvas.drawRect(x1, y1, x2, y2, pFill);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (resetOnRelease && e.getAction() == MotionEvent.ACTION_UP) {
            this.value = defaultValue;
            resetCursor();
        } else {
           if (orientation == Orientation.HORIZONTAL) {
               curPos = (int)e.getX();
               this.value = Math.round((e.getX() / width) * (maxValue - minValue) + minValue);
           } else {
               curPos = (int)e.getY();
               this.value = Math.round(((height - e.getY()) / height) * (maxValue - minValue) + minValue);
           }
        }
        value = Math.max(Math.min(value, maxValue), minValue);
        invalidate();
        notifyListeners();
        return true;
    }

    public void resetCursor() {
        curPos = (orientation == Orientation.HORIZONTAL ? width : height) * (value - minValue ) / (maxValue - minValue);
        if (orientation == Orientation.VERTICAL)
            curPos = height - curPos;  //because coordinate 0 is at the top, it is needed to adjust to position
    }

    public void setValue(int val) {
        this.value = Math.min(maxValue,Math.max(minValue, val));
        resetCursor();

        invalidate();
        notifyListeners();
    }

    public void setMaxValue(int maxVal) {
        this.maxValue = maxVal;
        value = Math.min(maxVal, value);
        resetCursor();

        invalidate();
        notifyListeners();
    }

    public void setMinValue(int minVal) {
        this.minValue = minVal;
        value = Math.max(minVal, value);
        resetCursor();

        invalidate();
        notifyListeners();
    }

    public void setDefaultValue(int defaultVal) {
        this.defaultValue = defaultVal;
        invalidate();
    }

    public void setLineThickness(int val) {
        this.lineThickness = Math.max(val, 1);
    }

    public void setHeight(int val) {
        this.height = val;
        resetCursor();
        invalidate();
        notifyListeners();
    }

    public void setWidth(int val) {
        this.width = val;
        resetCursor();
        invalidate();
        notifyListeners();
    }

    public int getValue() {
        return value;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public int getMaxValue() {
        return maxValue;
    }


    public int getMinValue() {
        return minValue;
    }


    public int getLineThickness() {
        return this.lineThickness;
    }

    public void addAxisControllerListener(AxisControllerListener l) {
        if (!this.listeners.contains(l))
            this.listeners.add(l);
    }

    public void removeAxisControllerListener(AxisControllerListener l) {
        this.listeners.remove(l);
    }

    private void notifyListeners() {
        for (AxisControllerListener l : listeners) {
            l.onValueChanged(this, value);
        }
    }

    public enum Orientation {VERTICAL, HORIZONTAL};
}
