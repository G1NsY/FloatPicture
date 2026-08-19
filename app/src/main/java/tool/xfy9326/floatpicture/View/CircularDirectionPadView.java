package tool.xfy9326.floatpicture.View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** A circular four-sector direction pad with tap and press-and-hold movement. */
public class CircularDirectionPadView extends View {
    public interface OnDirectionListener {
        boolean onMove(int deltaX, int deltaY);
    }

    private static final long REPEAT_START_DELAY_MS = 350L;
    private static final long REPEAT_INTERVAL_MS = 45L;
    private static final int REPEAT_STEP_PX = 5;
    private static final int[] DIRECTION_X = {0, 1, 0, -1};
    private static final int[] DIRECTION_Y = {-1, 0, 1, 0};

    private final Paint sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF circleBounds = new RectF();
    private final Path arrowPath = new Path();
    private OnDirectionListener directionListener;
    private int activeDirection = -1;
    private boolean repeating;

    private final Runnable repeatMovement = new Runnable() {
        @Override
        public void run() {
            if (!repeating || activeDirection < 0 || !isAttachedToWindow()) {
                repeating = false;
                return;
            }
            if (!moveActiveDirection(REPEAT_STEP_PX)) {
                repeating = false;
                return;
            }
            postDelayed(this, REPEAT_INTERVAL_MS);
        }
    };

    public CircularDirectionPadView(Context context) {
        super(context);
        setClickable(true);
        sectorPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeWidth(dp(2.4f));
        arrowPaint.setColor(Color.WHITE);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.rgb(40, 45, 55));
    }

    public void setOnDirectionListener(OnDirectionListener listener) {
        directionListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(164f);
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
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(4f);
        circleBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        for (int direction = 0; direction < 4; direction++) {
            sectorPaint.setColor(direction == activeDirection
                    ? Color.rgb(64, 196, 255)
                    : Color.rgb(64, 72, 86));
            float centerAngle = -90f + direction * 90f;
            canvas.drawArc(circleBounds, centerAngle - 42f, 84f, true, sectorPaint);
            drawArrow(canvas, cx, cy - radius * 0.61f, direction * 90f);
        }
        canvas.drawCircle(cx, cy, radius * 0.16f, centerPaint);
    }

    private void drawArrow(Canvas canvas, float x, float y, float rotation) {
        float halfWidth = dp(8f);
        float halfHeight = dp(5f);
        arrowPath.reset();
        arrowPath.moveTo(x - halfWidth, y + halfHeight);
        arrowPath.lineTo(x, y - halfHeight);
        arrowPath.lineTo(x + halfWidth, y + halfHeight);
        canvas.save();
        canvas.rotate(rotation, getWidth() / 2f, getHeight() / 2f);
        canvas.drawPath(arrowPath, arrowPaint);
        canvas.restore();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                int direction = directionAt(event.getX(), event.getY());
                if (direction < 0) {
                    return false;
                }
                activeDirection = direction;
                repeating = moveActiveDirection(1);
                removeCallbacks(repeatMovement);
                if (repeating) {
                    postDelayed(repeatMovement, REPEAT_START_DELAY_MS);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                int direction = directionAt(event.getX(), event.getY());
                if (direction >= 0 && direction != activeDirection) {
                    activeDirection = direction;
                    moveActiveDirection(1);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                stopMovement();
                performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                stopMovement();
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

    private int directionAt(float x, float y) {
        float dx = x - getWidth() / 2f;
        float dy = y - getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f;
        if (dx * dx + dy * dy > radius * radius) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle >= -45d && angle < 45d) return 1;
        if (angle >= 45d && angle < 135d) return 2;
        if (angle >= -135d && angle < -45d) return 0;
        return 3;
    }

    private boolean moveActiveDirection(int step) {
        return directionListener != null
                && directionListener.onMove(
                DIRECTION_X[activeDirection] * step,
                DIRECTION_Y[activeDirection] * step);
    }

    private void stopMovement() {
        repeating = false;
        removeCallbacks(repeatMovement);
        activeDirection = -1;
        invalidate();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
