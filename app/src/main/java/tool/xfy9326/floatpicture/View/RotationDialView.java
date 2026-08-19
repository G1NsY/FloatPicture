package tool.xfy9326.floatpicture.View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** A dense circular degree dial whose control point can be dragged around the rim. */
public class RotationDialView extends View {
    public interface OnAngleChangeListener {
        boolean onAngleChanged(float angle);
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnAngleChangeListener angleChangeListener;
    private float angle;

    public RotationDialView(Context context) {
        super(context);
        setClickable(true);
        backgroundPaint.setColor(Color.rgb(52, 59, 71));
        backgroundPaint.setStyle(Paint.Style.FILL);
        tickPaint.setColor(Color.LTGRAY);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        knobPaint.setColor(Color.rgb(64, 196, 255));
        knobPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(18f));
    }

    public void setOnAngleChangeListener(OnAngleChangeListener listener) {
        angleChangeListener = listener;
    }

    public void setAngle(float value) {
        angle = normalize(value);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(176f);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outerRadius = Math.min(getWidth(), getHeight()) / 2f - dp(5f);
        canvas.drawCircle(cx, cy, outerRadius, backgroundPaint);

        for (int index = 0; index < 72; index++) {
            double radians = Math.toRadians(index * 5d - 90d);
            boolean major = index % 6 == 0;
            float tickLength = dp(major ? 11f : 6f);
            tickPaint.setStrokeWidth(dp(major ? 2.2f : 1.2f));
            float innerRadius = outerRadius - tickLength;
            canvas.drawLine(
                    cx + (float) Math.cos(radians) * innerRadius,
                    cy + (float) Math.sin(radians) * innerRadius,
                    cx + (float) Math.cos(radians) * outerRadius,
                    cy + (float) Math.sin(radians) * outerRadius,
                    tickPaint);
        }

        double knobRadians = Math.toRadians(angle - 90f);
        float knobRadius = outerRadius - dp(13f);
        float knobX = cx + (float) Math.cos(knobRadians) * knobRadius;
        float knobY = cy + (float) Math.sin(knobRadians) * knobRadius;
        canvas.drawCircle(knobX, knobY, dp(9f), knobPaint);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f;
        canvas.drawText(String.format(Locale.ROOT, "%d°", Math.round(angle)), cx, textY, textPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                float nextAngle = angleAt(event.getX(), event.getY());
                if (angleChangeListener == null || angleChangeListener.onAngleChanged(nextAngle)) {
                    setAngle(nextAngle);
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float angleAt(float x, float y) {
        double degrees = Math.toDegrees(Math.atan2(
                y - getHeight() / 2f,
                x - getWidth() / 2f)) + 90d;
        return normalize((float) degrees);
    }

    private static float normalize(float value) {
        value %= 360f;
        if (value < 0f) value += 360f;
        return value;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
