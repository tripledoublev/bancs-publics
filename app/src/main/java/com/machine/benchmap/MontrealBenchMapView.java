package com.machine.benchmap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.DiscretePathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MontrealBenchMapView - v0.1.0
 * Refined Swiss-style local vector map engine.
 *
 * Highlights:
 * - 29,708 vector street segments with OpenGL-accelerated drawLines
 * - Zero-jump multi-touch pinch-to-zoom with exact focal point physics
 * - 2D Spatial Grid indexing for O(1) tap hit-testing and nearest bench search
 * - Compass heading orientation cone on user Swiss pin
 */
public class MontrealBenchMapView extends View {

    public interface BenchMapListener {
        void onBenchSelected(Bench bench, double distanceMeters);
        void onBenchDeselected();
        void onMapLoaded(int totalBenches);
        void onMapLongPressed(double lat, double lon, float screenX, float screenY);
    }

    public static class GeometryLayer {
        public final float[] coords; // Flat Mercator radians [mx0, my0, mx1, my1, ...]
        public final float minX, maxX, minY, maxY;
        public int frameId = 0;

        public GeometryLayer(float[] coords, float minX, float maxX, float minY, float maxY) {
            this.coords = coords;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    /**
     * High-speed 2D uniform grid for culling tens of thousands of street segments.
     * Prevents duplicate rendering across cell boundaries via frame IDs.
     */
    public static class SpatialStreetGrid {
        private final int rows;
        private final int cols;
        private final float minX, maxX, minY, maxY;
        private final float cellW, cellH;
        private final List<GeometryLayer>[][] cells;
        private int currentFrame = 1;

        @SuppressWarnings("unchecked")
        public SpatialStreetGrid(List<GeometryLayer> layers, int rows, int cols,
                                 float minX, float maxX, float minY, float maxY) {
            this.rows = rows;
            this.cols = cols;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.cellW = (maxX - minX) / cols;
            this.cellH = (maxY - minY) / rows;
            this.cells = new ArrayList[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    cells[r][c] = new ArrayList<>();
                }
            }

            for (int i = 0; i < layers.size(); i++) {
                GeometryLayer layer = layers.get(i);
                int c0 = Math.max(0, Math.min(cols - 1, (int) ((layer.minX - minX) / cellW)));
                int c1 = Math.max(0, Math.min(cols - 1, (int) ((layer.maxX - minX) / cellW)));
                int r0 = Math.max(0, Math.min(rows - 1, (int) ((layer.minY - minY) / cellH)));
                int r1 = Math.max(0, Math.min(rows - 1, (int) ((layer.maxY - minY) / cellH)));

                for (int r = r0; r <= r1; r++) {
                    for (int c = c0; c <= c1; c++) {
                        cells[r][c].add(layer);
                    }
                }
            }
        }

        public void drawVisible(Canvas canvas, float[] lineBuffer, Paint paint,
                                double viewMinX, double viewMaxX, double viewMinY, double viewMaxY,
                                float halfW, float halfH, double cX, double cY, float sc,
                                MapRenderStyle style, float density) {
            int frame = ++currentFrame;
            if (frame <= 0) {
                currentFrame = 1;
                frame = 1;
            }

            int c0 = Math.max(0, Math.min(cols - 1, (int) ((viewMinX - minX) / cellW)));
            int c1 = Math.max(0, Math.min(cols - 1, (int) ((viewMaxX - minX) / cellW)));
            int r0 = Math.max(0, Math.min(rows - 1, (int) ((viewMinY - minY) / cellH)));
            int r1 = Math.max(0, Math.min(rows - 1, (int) ((viewMaxY - minY) / cellH)));

            int bufIdx = 0;
            int maxCap = lineBuffer.length;

            boolean isDashed = (style == MapRenderStyle.DASHED_CADASTRAL);
            boolean isDotted = (style == MapRenderStyle.DOTTED_MATRIX);

            float dashLen = 5.5f * density;
            float gapLen = 3.8f * density;
            float dotStep = 4.8f * density;

            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    List<GeometryLayer> cellList = cells[r][c];
                    int sz = cellList.size();
                    for (int i = 0; i < sz; i++) {
                        GeometryLayer layer = cellList.get(i);
                        if (layer.frameId == frame) {
                            continue;
                        }
                        layer.frameId = frame;

                        if (layer.maxX < viewMinX || layer.minX > viewMaxX ||
                                layer.maxY < viewMinY || layer.minY > viewMaxY) {
                            continue;
                        }

                        float[] coords = layer.coords;
                        int numPts = coords.length;
                        for (int j = 0; j < numPts - 2; j += 2) {
                            float x0 = halfW + (float) ((coords[j] - cX) * sc);
                            float y0 = halfH - (float) ((coords[j + 1] - cY) * sc);
                            float x1 = halfW + (float) ((coords[j + 2] - cX) * sc);
                            float y1 = halfH - (float) ((coords[j + 3] - cY) * sc);

                            if (isDashed) {
                                float dx = x1 - x0;
                                float dy = y1 - y0;
                                float len = (float) Math.hypot(dx, dy);
                                if (len <= dashLen + gapLen) {
                                    if (len > gapLen * 0.7f) {
                                        if (bufIdx + 4 > maxCap) {
                                            canvas.drawLines(lineBuffer, 0, bufIdx, paint);
                                            bufIdx = 0;
                                        }
                                        lineBuffer[bufIdx++] = x0;
                                        lineBuffer[bufIdx++] = y0;
                                        lineBuffer[bufIdx++] = x0 + dx * 0.65f;
                                        lineBuffer[bufIdx++] = y0 + dy * 0.65f;
                                    }
                                } else {
                                    float nx = dx / len;
                                    float ny = dy / len;
                                    float t = 0f;
                                    while (t < len) {
                                        float tEnd = Math.min(len, t + dashLen);
                                        if (bufIdx + 4 > maxCap) {
                                            canvas.drawLines(lineBuffer, 0, bufIdx, paint);
                                            bufIdx = 0;
                                        }
                                        lineBuffer[bufIdx++] = x0 + nx * t;
                                        lineBuffer[bufIdx++] = y0 + ny * t;
                                        lineBuffer[bufIdx++] = x0 + nx * tEnd;
                                        lineBuffer[bufIdx++] = y0 + ny * tEnd;
                                        t += dashLen + gapLen;
                                    }
                                }
                            } else if (isDotted) {
                                float dx = x1 - x0;
                                float dy = y1 - y0;
                                float len = (float) Math.hypot(dx, dy);
                                float nx = (len > 0.001f) ? (dx / len) : 0f;
                                float ny = (len > 0.001f) ? (dy / len) : 0f;
                                float t = 0f;
                                while (t <= len) {
                                    if (bufIdx + 4 > maxCap) {
                                        canvas.drawLines(lineBuffer, 0, bufIdx, paint);
                                        bufIdx = 0;
                                    }
                                    float px = x0 + nx * t;
                                    float py = y0 + ny * t;
                                    lineBuffer[bufIdx++] = px;
                                    lineBuffer[bufIdx++] = py;
                                    lineBuffer[bufIdx++] = px;
                                    lineBuffer[bufIdx++] = py;
                                    t += dotStep;
                                }
                            } else {
                                if (bufIdx + 4 > maxCap) {
                                    canvas.drawLines(lineBuffer, 0, bufIdx, paint);
                                    bufIdx = 0;
                                }
                                lineBuffer[bufIdx++] = x0;
                                lineBuffer[bufIdx++] = y0;
                                lineBuffer[bufIdx++] = x1;
                                lineBuffer[bufIdx++] = y1;
                            }
                        }
                    }
                }
            }

            if (bufIdx > 0) {
                canvas.drawLines(lineBuffer, 0, bufIdx, paint);
            }
        }
    }

    // Colors - Refined Architectural Swiss Palette (Light)
    private static final int LIGHT_WATER = Color.parseColor("#E6ECF1");         // Crisp architectural Nordic water
    private static final int LIGHT_LAND = Color.parseColor("#F5F5F7");          // Pure clean landmass / paper
    private static final int LIGHT_SHORELINE = Color.parseColor("#D0D6DC");     // Subtle hairline perimeter
    private static final int LIGHT_PARK = Color.parseColor("#E5F2E8");         // Serene organic sage green
    private static final int LIGHT_PARK_BORDER = Color.parseColor("#C3D9C7");   // Park boundary hairline
    private static final int LIGHT_STREET_MAJOR = Color.parseColor("#94A3B8");  // Slate - elegant arterial lines
    private static final int LIGHT_STREET_MINOR = Color.parseColor("#CBD5E1");  // Whisper hairline residential
    private static final int LIGHT_BENCH_STREET = Color.parseColor("#121212");  // Swiss charcoal / Ink
    private static final int LIGHT_BENCH_PARK = Color.parseColor("#2B7A4B");    // Forest emerald
    private static final int LIGHT_BENCH_HALO = Color.parseColor("#F5F5F7");
    private static final int LIGHT_BENCH_GAP = Color.parseColor("#F5F5F7");

    // Nocturne Swiss Minimalist Palette (Dark / OLED)
    private static final int DARK_WATER = Color.parseColor("#090A0C");          // Deep midnight oceanic
    private static final int DARK_LAND = Color.parseColor("#121316");           // Matte obsidian slate
    private static final int DARK_SHORELINE = Color.parseColor("#1C1E24");      // Subtle shoreline contour
    private static final int DARK_PARK = Color.parseColor("#141F18");          // Nocturnal botanical emerald
    private static final int DARK_PARK_BORDER = Color.parseColor("#1D2E23");    // Park boundary hairline
    private static final int DARK_STREET_MAJOR = Color.parseColor("#2E323B");   // Visible arterial network
    private static final int DARK_STREET_MINOR = Color.parseColor("#1A1C22");   // Whisper neighbourhood grid
    private static final int DARK_BENCH_STREET = Color.parseColor("#F5F5F7");   // Crisp platinum chalk dots
    private static final int DARK_BENCH_PARK = Color.parseColor("#34C759");     // Luminous mint emerald dots
    private static final int DARK_BENCH_HALO = Color.parseColor("#121316");     // Dark separation ring (same as DARK_LAND)
    private static final int DARK_BENCH_GAP = Color.parseColor("#121316");

    private static final int COLOR_SWISS_RED = Color.parseColor("#E52B35");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");

    private boolean isDarkMode = false;

    // Paints
    private final Paint paintLand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintShoreline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintParkBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStreetMajor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStreetMinor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchStreet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchPark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchSelected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchSelectedGap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchSelectedCore = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Custom Bench Paints & Diamond Path
    private final Paint paintCustomBenchHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCustomBenchFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCustomBenchStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCustomBenchCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path customDiamondPath = new Path();

    // User Pin & Heading Cone Paints
    private final Paint paintPinFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPinStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPinDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPinShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAccuracyFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAccuracyStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHeadingCone = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Friend / Rendez-vous Meetup State & Paints
    private Double friendLat = null;
    private Double friendLon = null;
    private int friendBlurMeters = 0;
    private final List<Bench> meetupBenches = new ArrayList<>();
    private final Paint paintFriendPinFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFriendPinStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFriendBlurFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFriendBlurStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMeetupLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMeetupBenchHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMeetupBenchCore = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Reusable Paths & Rects
    private final Path pathPoly = new Path();
    private final Path pinPath = new Path();
    private final Path friendPinPath = new Path();
    private final Path headingPath = new Path();
    private final RectF headingArcRect = new RectF();

    // Reusable line batch buffer for hardware drawLines (avoids Path allocations)
    private final float[] lineBuffer = new float[8192];

    // Map Rotation & Two-Finger Twist
    private float mapRotationDegrees = 0f;
    private double lastRotationAngle = 0;
    private boolean isRotating = false;
    private ValueAnimator rotationAnimator = null;
    private boolean showFloatingCompass = false;

    public interface OnRotationChangeListener {
        void onRotationChanged(float rotationDegrees);
    }
    private OnRotationChangeListener rotationChangeListener = null;

    public void setOnRotationChangeListener(OnRotationChangeListener listener) {
        this.rotationChangeListener = listener;
        if (listener != null) {
            listener.onRotationChanged(mapRotationDegrees);
        }
    }

    private void notifyRotationChanged() {
        if (rotationChangeListener != null) {
            rotationChangeListener.onRotationChanged(mapRotationDegrees);
        }
    }

    public void setShowFloatingCompass(boolean show) {
        this.showFloatingCompass = show;
        invalidate();
    }

    private final RectF compassBounds = new RectF();
    private float compassTopPx = -1f;
    private final Paint paintCompassBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCompassStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCompassNorthNeedle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCompassSouthNeedle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCompassText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path compassNorthPath = new Path();
    private final Path compassSouthPath = new Path();

    // Map Render Style & Daily Rotation
    private MapRenderStyle currentStyle = MapRenderStyle.SWISS_CLEAN;
    private boolean isDailyAuto = true;
    private final Paint paintStreetCasingCore = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Pre-allocated PathEffects for MapRenderStyle
    private PathEffect effectDashedShoreline;
    private PathEffect effectDashedPark;
    private PathEffect effectDottedShoreline;
    private PathEffect effectDottedPark;

    // Geographic center of Montreal (Mount Royal)
    public static final double CENTER_LAT = 45.50884;
    public static final double CENTER_LON = -73.58781;
    private double centerMercX = lonToMercatorX(CENTER_LON);
    private double centerMercY = latToMercatorY(CENTER_LAT);

    // Zoom & Limits
    private static final float MIN_SCALE = 55000f;      // Whole metropolitan island
    private static final float MAX_SCALE = 65000000f;   // Deep architectural resolution (~70m street width)
    private float scale = 220000f;                      // Default Montreal overview
    private float density = 1.0f;

    // Gestures & Physics
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OverScroller scroller;
    private ValueAnimator animator;
    private float lastFocusX, lastFocusY;
    private int lastFlingX, lastFlingY;
    private boolean isScaling = false;
    private long lastScaleEndTime = 0;
    private long lastPointerUpTime = 0;
    private long lastDoubleTapTime = 0;
    private long lastSingleTapUpTime = 0;
    private float lastSingleTapUpX = 0;
    private float lastSingleTapUpY = 0;
    private boolean gestureHadMultiTouch = false;
    private boolean gestureHadMovement = false;
    private float touchDownX = 0f;
    private float touchDownY = 0f;

    // Vector Data & Spatial Index
    private final Object dataLock = new Object();
    private final List<GeometryLayer> islandPolys = new ArrayList<>();
    private final List<GeometryLayer> parkPolys = new ArrayList<>();
    private final List<GeometryLayer> majorStreets = new ArrayList<>();
    private final List<GeometryLayer> minorStreets = new ArrayList<>();
    private final List<Bench> allBenches = new ArrayList<>();
    private final List<Bench> customBenches = new ArrayList<>();
    private volatile SpatialBenchIndex spatialIndex = null;
    private volatile boolean isMapReady = false;

    // Spatial street grids for instant zero-lag rendering
    private static final float GRID_MIN_X = -1.2925f;
    private static final float GRID_MAX_X = -1.2815f;
    private static final float GRID_MIN_Y = 0.8895f;
    private static final float GRID_MAX_Y = 0.9000f;

    private SpatialStreetGrid majorGrid = null;
    private SpatialStreetGrid minorGrid = null;
    private final List<Bench> visibleBenches = new ArrayList<>(2048);

    // State
    private Bench selectedBench = null;
    private float headerBottomPx = 0f;

    public void setHeaderBottomPx(float bottomPx) {
        this.headerBottomPx = bottomPx;
    }
    private Location userLocation = null;
    private float userHeadingDegrees = -1f;
    private BenchMapListener mapListener;

    public MontrealBenchMapView(Context context) {
        super(context);
        init();
    }

    public MontrealBenchMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MontrealBenchMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        scroller = new OverScroller(getContext());

        // Landmass & Coastline
        paintLand.setStyle(Paint.Style.FILL);

        paintShoreline.setStyle(Paint.Style.STROKE);
        paintShoreline.setStrokeWidth(1.1f * density);

        // Parks & Green Spaces
        paintPark.setStyle(Paint.Style.FILL);

        paintParkBorder.setStyle(Paint.Style.STROKE);
        paintParkBorder.setStrokeWidth(0.75f * density);

        // Major Streets (Clean Slate - BUTT & MITER for high FPS GPU throughput)
        paintStreetMajor.setStyle(Paint.Style.STROKE);
        paintStreetMajor.setStrokeWidth(1.5f * density);
        paintStreetMajor.setStrokeCap(Paint.Cap.BUTT);
        paintStreetMajor.setStrokeJoin(Paint.Join.MITER);

        // Minor Streets (Subtle Hairline - BUTT & MITER for high FPS GPU throughput)
        paintStreetMinor.setStyle(Paint.Style.STROKE);
        paintStreetMinor.setStrokeWidth(0.75f * density);
        paintStreetMinor.setStrokeCap(Paint.Cap.BUTT);
        paintStreetMinor.setStrokeJoin(Paint.Join.MITER);

        // Major Streets Casing Core (for DOUBLE_CASING boulevard style)
        paintStreetCasingCore.setStyle(Paint.Style.STROKE);
        paintStreetCasingCore.setStrokeCap(Paint.Cap.BUTT);
        paintStreetCasingCore.setStrokeJoin(Paint.Join.MITER);

        // Reusable Map Style PathEffects
        effectDashedShoreline = new DashPathEffect(new float[]{14f * density, 8f * density}, 0);
        effectDashedPark = new DashPathEffect(new float[]{8f * density, 6f * density}, 0);
        effectDottedShoreline = new DashPathEffect(new float[]{2.5f * density, 5.5f * density}, 0);
        effectDottedPark = new DashPathEffect(new float[]{2.0f * density, 5.0f * density}, 0);

        // Benches
        paintBenchStreet.setStyle(Paint.Style.FILL);
        paintBenchPark.setStyle(Paint.Style.FILL);
        paintBenchHalo.setStyle(Paint.Style.FILL);

        // Selected Bench Rings
        paintBenchSelected.setStyle(Paint.Style.STROKE);
        paintBenchSelected.setStrokeWidth(2.4f * density);

        paintBenchSelectedGap.setStyle(Paint.Style.STROKE);
        paintBenchSelectedGap.setStrokeWidth(2.0f * density);

        paintBenchSelectedCore.setStyle(Paint.Style.FILL);

        // Custom Bench Paints
        paintCustomBenchHalo.setStyle(Paint.Style.STROKE);
        paintCustomBenchHalo.setStrokeWidth(2.8f * density);

        paintCustomBenchFill.setStyle(Paint.Style.FILL);
        paintCustomBenchFill.setColor(Color.parseColor("#FF6D00")); // Vibrant Terracotta / Amber

        paintCustomBenchStroke.setStyle(Paint.Style.STROKE);
        paintCustomBenchStroke.setStrokeWidth(1.2f * density);
        paintCustomBenchStroke.setColor(Color.parseColor("#D84315"));

        paintCustomBenchCenter.setStyle(Paint.Style.FILL);
        paintCustomBenchCenter.setColor(COLOR_WHITE);

        // User Swiss Pin
        paintPinFill.setStyle(Paint.Style.FILL);

        paintPinStroke.setStyle(Paint.Style.STROKE);
        paintPinStroke.setStrokeWidth(1.8f * density);

        paintPinDot.setStyle(Paint.Style.FILL);
        paintPinShadow.setStyle(Paint.Style.FILL);

        paintAccuracyFill.setStyle(Paint.Style.FILL);

        paintAccuracyStroke.setStyle(Paint.Style.STROKE);
        paintAccuracyStroke.setStrokeWidth(1.0f * density);

        paintHeadingCone.setStyle(Paint.Style.FILL);

        // Friend Pin & Meetup Paints
        paintFriendPinFill.setStyle(Paint.Style.FILL);
        paintFriendPinStroke.setStyle(Paint.Style.STROKE);
        paintFriendPinStroke.setStrokeWidth(1.8f * density);

        paintFriendBlurFill.setStyle(Paint.Style.FILL);
        paintFriendBlurStroke.setStyle(Paint.Style.STROKE);
        paintFriendBlurStroke.setStrokeWidth(1.2f * density);

        paintMeetupLine.setStyle(Paint.Style.STROKE);
        paintMeetupLine.setStrokeWidth(2.0f * density);
        paintMeetupLine.setPathEffect(new DashPathEffect(new float[]{14f * density, 10f * density}, 0));

        paintMeetupBenchHalo.setStyle(Paint.Style.STROKE);
        paintMeetupBenchHalo.setStrokeWidth(2.2f * density);
        paintMeetupBenchCore.setStyle(Paint.Style.FILL);

        paintCompassBg.setStyle(Paint.Style.FILL);
        paintCompassStroke.setStyle(Paint.Style.STROKE);
        paintCompassStroke.setStrokeWidth(1.2f * density);
        paintCompassNorthNeedle.setStyle(Paint.Style.FILL);
        paintCompassNorthNeedle.setColor(COLOR_SWISS_RED);
        paintCompassSouthNeedle.setStyle(Paint.Style.FILL);
        paintCompassText.setStyle(Paint.Style.FILL);
        paintCompassText.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paintCompassText.setTextAlign(Paint.Align.CENTER);

        applyThemeColors();

        initGestures();
        loadVectorDataBinary();
    }

    public void setDarkMode(boolean darkMode) {
        if (this.isDarkMode == darkMode) return;
        this.isDarkMode = darkMode;
        applyThemeColors();
        invalidate();
    }

    public boolean isDarkMode() {
        return isDarkMode;
    }

    private void applyThemeColors() {
        if (isDarkMode) {
            paintLand.setColor(DARK_LAND);
            paintShoreline.setColor(DARK_SHORELINE);
            paintPark.setColor(DARK_PARK);
            paintParkBorder.setColor(DARK_PARK_BORDER);
            paintStreetMajor.setColor(DARK_STREET_MAJOR);
            paintStreetMinor.setColor(DARK_STREET_MINOR);
            paintBenchStreet.setColor(DARK_BENCH_STREET);
            paintBenchPark.setColor(DARK_BENCH_PARK);
            paintBenchHalo.setColor(DARK_BENCH_HALO);
            paintCustomBenchHalo.setColor(DARK_LAND);
            paintBenchSelected.setColor(COLOR_SWISS_RED);
            paintBenchSelectedGap.setColor(DARK_BENCH_GAP);
            paintBenchSelectedCore.setColor(COLOR_SWISS_RED);

            paintPinFill.setColor(COLOR_SWISS_RED);
            paintPinStroke.setColor(DARK_LAND);
            paintPinDot.setColor(COLOR_WHITE);
            paintPinShadow.setColor(Color.argb(85, 0, 0, 0));

            paintAccuracyFill.setColor(COLOR_SWISS_RED);
            paintAccuracyFill.setAlpha(25);
            paintAccuracyStroke.setColor(COLOR_SWISS_RED);
            paintAccuracyStroke.setAlpha(70);

            paintHeadingCone.setColor(COLOR_SWISS_RED);
            paintHeadingCone.setAlpha(40);

            paintFriendPinFill.setColor(Color.parseColor("#3B82F6"));
            paintFriendPinStroke.setColor(DARK_LAND);
            paintFriendBlurFill.setColor(Color.argb(35, 59, 130, 246));
            paintFriendBlurStroke.setColor(Color.argb(120, 59, 130, 246));
            paintMeetupLine.setColor(Color.parseColor("#60A5FA"));

            paintCompassBg.setColor(Color.parseColor("#181A20"));
            paintCompassStroke.setColor(Color.parseColor("#262932"));
            paintCompassSouthNeedle.setColor(Color.parseColor("#8E93A0"));
            paintCompassText.setColor(Color.parseColor("#F4F5F7"));
        } else {
            paintLand.setColor(LIGHT_LAND);
            paintShoreline.setColor(LIGHT_SHORELINE);
            paintPark.setColor(LIGHT_PARK);
            paintParkBorder.setColor(LIGHT_PARK_BORDER);
            paintStreetMajor.setColor(LIGHT_STREET_MAJOR);
            paintStreetMinor.setColor(LIGHT_STREET_MINOR);
            paintBenchStreet.setColor(LIGHT_BENCH_STREET);
            paintBenchPark.setColor(LIGHT_BENCH_PARK);
            paintBenchHalo.setColor(LIGHT_BENCH_HALO);
            paintCustomBenchHalo.setColor(COLOR_WHITE);
            paintBenchSelected.setColor(COLOR_SWISS_RED);
            paintBenchSelectedGap.setColor(LIGHT_BENCH_GAP);
            paintBenchSelectedCore.setColor(COLOR_SWISS_RED);

            paintPinFill.setColor(COLOR_SWISS_RED);
            paintPinStroke.setColor(COLOR_WHITE);
            paintPinDot.setColor(COLOR_WHITE);
            paintPinShadow.setColor(Color.argb(55, 30, 41, 59));

            paintAccuracyFill.setColor(COLOR_SWISS_RED);
            paintAccuracyFill.setAlpha(20);
            paintAccuracyStroke.setColor(COLOR_SWISS_RED);
            paintAccuracyStroke.setAlpha(65);

            paintHeadingCone.setColor(COLOR_SWISS_RED);
            paintHeadingCone.setAlpha(35);

            paintFriendPinFill.setColor(Color.parseColor("#2563EB"));
            paintFriendPinStroke.setColor(COLOR_WHITE);
            paintFriendBlurFill.setColor(Color.argb(30, 37, 99, 235));
            paintFriendBlurStroke.setColor(Color.argb(100, 37, 99, 235));
            paintMeetupLine.setColor(Color.parseColor("#3B82F6"));

            paintCompassBg.setColor(Color.WHITE);
            paintCompassStroke.setColor(Color.parseColor("#E4E7EC"));
            paintCompassSouthNeedle.setColor(Color.parseColor("#94A3B8"));
            paintCompassText.setColor(Color.parseColor("#111318"));
        }

        paintMeetupBenchHalo.setColor(Color.parseColor("#F59E0B"));
        paintMeetupBenchCore.setColor(Color.parseColor("#F59E0B"));
        paintStreetCasingCore.setColor(isDarkMode ? DARK_LAND : LIGHT_LAND);

        updatePaintsForStyle();
    }

    public void setRenderStyle(MapRenderStyle style, boolean dailyAuto) {
        this.currentStyle = (style != null) ? style : MapRenderStyle.SWISS_CLEAN;
        this.isDailyAuto = dailyAuto;
        applyThemeColors();
        invalidate();
    }

    public MapRenderStyle getRenderStyle() {
        return currentStyle;
    }

    public boolean isDailyAuto() {
        return isDailyAuto;
    }

    public MapRenderStyle getActiveEffectiveStyle() {
        return isDailyAuto ? MapRenderStyle.getDailyStyle() : currentStyle;
    }

    private void updatePaintsForStyle() {
        MapRenderStyle style = getActiveEffectiveStyle();

        // Reset path effects and stroke caps by default
        paintShoreline.setPathEffect(null);
        paintParkBorder.setPathEffect(null);
        paintStreetMajor.setPathEffect(null);
        paintStreetMinor.setPathEffect(null);
        paintStreetMajor.setStrokeCap(Paint.Cap.BUTT);
        paintStreetMinor.setStrokeCap(Paint.Cap.BUTT);

        switch (style) {
            case THIN_ARCHITECTURAL:
                paintShoreline.setStrokeWidth(0.5f * density);
                paintParkBorder.setStrokeWidth(0.35f * density);
                paintStreetMajor.setStrokeCap(Paint.Cap.BUTT);
                paintStreetMinor.setStrokeCap(Paint.Cap.BUTT);
                if (isDarkMode) {
                    paintShoreline.setColor(Color.parseColor("#334155"));
                    paintPark.setColor(Color.parseColor("#101B15"));
                    paintParkBorder.setColor(Color.parseColor("#1E3A2B"));
                    paintStreetMajor.setColor(Color.parseColor("#64748B"));
                    paintStreetMinor.setColor(Color.parseColor("#334155"));
                    paintLand.setColor(Color.parseColor("#0D1117"));
                } else {
                    paintShoreline.setColor(Color.parseColor("#B0B9C4"));
                    paintPark.setColor(Color.parseColor("#EDF6F0"));
                    paintParkBorder.setColor(Color.parseColor("#9CBFA5"));
                    paintStreetMajor.setColor(Color.parseColor("#475569"));
                    paintStreetMinor.setColor(Color.parseColor("#94A3B8"));
                    paintLand.setColor(Color.parseColor("#FDFDFE"));
                }
                break;

            case BOLD_BAUHAUS:
                paintShoreline.setStrokeWidth(5.0f * density);
                paintParkBorder.setStrokeWidth(3.2f * density);
                paintStreetMajor.setStrokeCap(Paint.Cap.SQUARE);
                paintStreetMinor.setStrokeCap(Paint.Cap.SQUARE);
                if (isDarkMode) {
                    paintShoreline.setColor(Color.parseColor("#F1F5F9"));
                    paintPark.setColor(Color.parseColor("#0A2B18"));
                    paintParkBorder.setColor(Color.parseColor("#22C55E"));
                    paintStreetMajor.setColor(Color.parseColor("#FFFFFF"));
                    paintStreetMinor.setColor(Color.parseColor("#CBD5E1"));
                    paintLand.setColor(Color.parseColor("#0C0D10"));
                } else {
                    paintShoreline.setColor(Color.parseColor("#0A0A0C"));
                    paintPark.setColor(Color.parseColor("#C6E7CD"));
                    paintParkBorder.setColor(Color.parseColor("#1B4328"));
                    paintStreetMajor.setColor(Color.parseColor("#0A0A0C"));
                    paintStreetMinor.setColor(Color.parseColor("#2B2D33"));
                    paintLand.setColor(Color.parseColor("#FBFBFC"));
                }
                break;

            case DASHED_CADASTRAL:
                paintShoreline.setStrokeWidth(1.8f * density);
                paintShoreline.setPathEffect(effectDashedShoreline);
                paintParkBorder.setStrokeWidth(1.2f * density);
                paintParkBorder.setPathEffect(effectDashedPark);
                paintStreetMajor.setStrokeCap(Paint.Cap.BUTT);
                paintStreetMinor.setStrokeCap(Paint.Cap.BUTT);
                if (isDarkMode) {
                    paintLand.setColor(Color.parseColor("#141311"));
                    paintShoreline.setColor(Color.parseColor("#A8A29E"));
                    paintPark.setColor(Color.parseColor("#152219"));
                    paintParkBorder.setColor(Color.parseColor("#385842"));
                    paintStreetMajor.setColor(Color.parseColor("#D6D3D1"));
                    paintStreetMinor.setColor(Color.parseColor("#78716C"));
                } else {
                    paintLand.setColor(Color.parseColor("#F7F5EE"));
                    paintShoreline.setColor(Color.parseColor("#78716C"));
                    paintPark.setColor(Color.parseColor("#E3ECE4"));
                    paintParkBorder.setColor(Color.parseColor("#84A98C"));
                    paintStreetMajor.setColor(Color.parseColor("#44403C"));
                    paintStreetMinor.setColor(Color.parseColor("#78716C"));
                }
                break;

            case DOTTED_MATRIX:
                paintShoreline.setStrokeWidth(3.2f * density);
                paintShoreline.setStrokeCap(Paint.Cap.ROUND);
                paintShoreline.setPathEffect(effectDottedShoreline);
                paintParkBorder.setStrokeWidth(2.4f * density);
                paintParkBorder.setStrokeCap(Paint.Cap.ROUND);
                paintParkBorder.setPathEffect(effectDottedPark);
                paintStreetMajor.setStrokeCap(Paint.Cap.ROUND);
                paintStreetMinor.setStrokeCap(Paint.Cap.ROUND);
                if (isDarkMode) {
                    paintLand.setColor(Color.parseColor("#0B0F14"));
                    paintShoreline.setColor(Color.parseColor("#38BDF8"));
                    paintPark.setColor(Color.parseColor("#062417"));
                    paintParkBorder.setColor(Color.parseColor("#10B981"));
                    paintStreetMajor.setColor(Color.parseColor("#2DD4BF"));
                    paintStreetMinor.setColor(Color.parseColor("#0D9488"));
                } else {
                    paintLand.setColor(Color.parseColor("#F1F5F9"));
                    paintShoreline.setColor(Color.parseColor("#0F172A"));
                    paintPark.setColor(Color.parseColor("#DCFCE7"));
                    paintParkBorder.setColor(Color.parseColor("#16A34A"));
                    paintStreetMajor.setColor(Color.parseColor("#0F172A"));
                    paintStreetMinor.setColor(Color.parseColor("#475569"));
                }
                break;

            case ARTISTIC_BRUSH:
                paintShoreline.setStrokeWidth(2.6f * density);
                paintShoreline.setStrokeCap(Paint.Cap.ROUND);
                paintShoreline.setStrokeJoin(Paint.Join.ROUND);
                paintParkBorder.setStrokeWidth(1.6f * density);
                paintParkBorder.setStrokeCap(Paint.Cap.ROUND);
                paintParkBorder.setStrokeJoin(Paint.Join.ROUND);
                paintStreetMajor.setStrokeCap(Paint.Cap.ROUND);
                paintStreetMajor.setStrokeJoin(Paint.Join.ROUND);
                paintStreetMinor.setStrokeCap(Paint.Cap.ROUND);
                paintStreetMinor.setStrokeJoin(Paint.Join.ROUND);
                if (isDarkMode) {
                    paintLand.setColor(Color.parseColor("#141312"));
                    paintShoreline.setColor(Color.parseColor("#A8A29E"));
                    paintPark.setColor(Color.parseColor("#162319"));
                    paintParkBorder.setColor(Color.parseColor("#44694E"));
                    paintStreetMajor.setColor(Color.parseColor("#E7E5E4"));
                    paintStreetMinor.setColor(Color.parseColor("#A8A29E"));
                } else {
                    paintLand.setColor(Color.parseColor("#FAF7F0"));
                    paintShoreline.setColor(Color.parseColor("#57534E"));
                    paintPark.setColor(Color.parseColor("#DDE7DC"));
                    paintParkBorder.setColor(Color.parseColor("#6B8A70"));
                    paintStreetMajor.setColor(Color.parseColor("#292524"));
                    paintStreetMinor.setColor(Color.parseColor("#57534E"));
                }
                break;

            case DOUBLE_CASING:
                paintShoreline.setStrokeWidth(1.5f * density);
                paintParkBorder.setStrokeWidth(1.0f * density);
                paintStreetMajor.setStrokeCap(Paint.Cap.BUTT);
                paintStreetMinor.setStrokeCap(Paint.Cap.BUTT);
                if (isDarkMode) {
                    paintLand.setColor(Color.parseColor("#0F141C"));
                    paintShoreline.setColor(Color.parseColor("#334155"));
                    paintPark.setColor(Color.parseColor("#0B2419"));
                    paintParkBorder.setColor(Color.parseColor("#15803D"));
                    paintStreetMajor.setColor(Color.parseColor("#94A3B8"));
                    paintStreetCasingCore.setColor(Color.parseColor("#0F141C"));
                    paintStreetMinor.setColor(Color.parseColor("#475569"));
                } else {
                    paintLand.setColor(Color.parseColor("#FAFBFD"));
                    paintShoreline.setColor(Color.parseColor("#334155"));
                    paintPark.setColor(Color.parseColor("#E0EFE5"));
                    paintParkBorder.setColor(Color.parseColor("#3B6E47"));
                    paintStreetMajor.setColor(Color.parseColor("#1E293B"));
                    paintStreetCasingCore.setColor(Color.parseColor("#FAFBFD"));
                    paintStreetMinor.setColor(Color.parseColor("#64748B"));
                }
                break;

            case DESATURATED_NOIR:
                paintShoreline.setStrokeWidth(1.8f * density);
                paintParkBorder.setStrokeWidth(1.0f * density);
                paintStreetMajor.setStrokeCap(Paint.Cap.BUTT);
                paintStreetMinor.setStrokeCap(Paint.Cap.BUTT);
                if (isDarkMode) {
                    paintLand.setColor(Color.parseColor("#09090B"));
                    paintShoreline.setColor(Color.parseColor("#FFFFFF"));
                    paintPark.setColor(Color.parseColor("#18181B"));
                    paintParkBorder.setColor(Color.parseColor("#52525B"));
                    paintStreetMajor.setColor(Color.parseColor("#FFFFFF"));
                    paintStreetMinor.setColor(Color.parseColor("#71717A"));
                    paintBenchStreet.setColor(Color.parseColor("#FF453A"));
                    paintBenchPark.setColor(Color.parseColor("#FF453A"));
                } else {
                    paintLand.setColor(Color.parseColor("#FFFFFF"));
                    paintShoreline.setColor(Color.parseColor("#000000"));
                    paintPark.setColor(Color.parseColor("#E4E4E7"));
                    paintParkBorder.setColor(Color.parseColor("#18181B"));
                    paintStreetMajor.setColor(Color.parseColor("#000000"));
                    paintStreetMinor.setColor(Color.parseColor("#52525B"));
                    paintBenchStreet.setColor(Color.parseColor("#E52B35"));
                    paintBenchPark.setColor(Color.parseColor("#E52B35"));
                }
                break;

            case SWISS_CLEAN:
            default:
                paintShoreline.setStrokeWidth(1.1f * density);
                paintParkBorder.setStrokeWidth(0.75f * density);
                break;
        }
    }

    private void initGestures() {
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                gestureHadMultiTouch = true;
                scroller.forceFinished(true);
                if (animator != null && animator.isRunning()) animator.cancel();
                lastFocusX = detector.getFocusX();
                lastFocusY = detector.getFocusY();
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                isScaling = true;
                gestureHadMultiTouch = true;
                float factor = detector.getScaleFactor();
                if (Float.isNaN(factor) || Float.isInfinite(factor) || factor <= 0f) return true;

                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();

                float oldScale = scale;
                float targetScale = oldScale * factor;
                float newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale));
                if (newScale == oldScale) {
                    lastFocusX = focusX;
                    lastFocusY = focusY;
                    return true;
                }

                // Exact closed-form focal-point zoom invariance and simultaneous focal pan
                float wHalf = getWidth() * 0.5f;
                float hHalf = getHeight() * 0.5f;

                centerMercX = centerMercX + (lastFocusX - wHalf) / oldScale - (focusX - wHalf) / newScale;
                centerMercY = centerMercY - (lastFocusY - hHalf) / oldScale + (focusY - hHalf) / newScale;

                scale = newScale;
                lastFocusX = focusX;
                lastFocusY = focusY;

                invalidate();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                isScaling = false;
                gestureHadMultiTouch = true;
                lastScaleEndTime = System.currentTimeMillis();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            scaleDetector.setQuickScaleEnabled(false);
        }

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                if (System.currentTimeMillis() - lastDoubleTapTime > 400) {
                    if (animator != null && animator.isRunning()) animator.cancel();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                gestureHadMovement = true;
                // Suppress single-finger scroll jumps when multiple touches or right after scaling or double-tap
                if (isScaling || isRotating || scaleDetector.isInProgress() || gestureHadMultiTouch || (e2 != null && e2.getPointerCount() > 1)) {
                    return false;
                }
                long now = System.currentTimeMillis();
                if (now - lastPointerUpTime < 300 || now - lastScaleEndTime < 300 || now - lastDoubleTapTime < 400) {
                    return false;
                }

                if (mapRotationDegrees != 0f) {
                    double rad = Math.toRadians(mapRotationDegrees);
                    float cos = (float) Math.cos(rad);
                    float sin = (float) Math.sin(rad);
                    float rotDistX = distanceX * cos + distanceY * sin;
                    float rotDistY = -distanceX * sin + distanceY * cos;
                    centerMercX += rotDistX / scale;
                    centerMercY -= rotDistY / scale;
                } else {
                    centerMercX += distanceX / scale;
                    centerMercY -= distanceY / scale;
                }
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isScaling || isRotating || scaleDetector.isInProgress() || gestureHadMultiTouch || (e2 != null && e2.getPointerCount() > 1)) {
                    return false;
                }
                long now = System.currentTimeMillis();
                if (now - lastPointerUpTime < 300 || now - lastScaleEndTime < 300 || now - lastDoubleTapTime < 400) {
                    return false;
                }

                scroller.forceFinished(true);
                scroller.fling(0, 0, (int) velocityX, (int) velocityY,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
                lastFlingX = 0;
                lastFlingY = 0;
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                long now = System.currentTimeMillis();
                boolean recentlyPinched = (now - lastPointerUpTime < 500) || (now - lastScaleEndTime < 500);
                if (!isScaling && !isRotating && !gestureHadMultiTouch && !gestureHadMovement && !recentlyPinched) {
                    handleTap(e.getX(), e.getY());
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                lastDoubleTapTime = System.currentTimeMillis();
                if (!isScaling && !isRotating && !gestureHadMultiTouch) {
                    double tapMercX = screenToMercX(e.getX(), e.getY());
                    double tapMercY = screenToMercY(e.getX(), e.getY());
                    animateToMerc(tapMercX, tapMercY, Math.min(MAX_SCALE, scale * 2.2f));
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                long now = System.currentTimeMillis();
                boolean recentlyPinched = (now - lastPointerUpTime < 600) || (now - lastScaleEndTime < 600);
                if (isScaling || isRotating || scaleDetector.isInProgress() || gestureHadMultiTouch || gestureHadMovement || recentlyPinched || (e != null && e.getPointerCount() > 1)) {
                    return;
                }
                float touchX = e.getX();
                float touchY = e.getY();
                if (touchY <= headerBottomPx) {
                    return;
                }
                if (showFloatingCompass && compassBounds.contains(touchX, touchY)) {
                    return;
                }

                double touchMercX = screenToMercX(touchX, touchY);
                double touchMercY = screenToMercY(touchX, touchY);
                float hitRadiusPx = 18f * density;
                double hitRadiusMerc = hitRadiusPx / scale;

                Bench hit = null;
                synchronized (dataLock) {
                    for (int i = 0; i < customBenches.size(); i++) {
                        Bench cb = customBenches.get(i);
                        double dx = (cb.mercX - touchMercX) * scale;
                        double dy = (cb.mercY - touchMercY) * scale;
                        if (Math.hypot(dx, dy) <= hitRadiusPx) {
                            hit = cb;
                            break;
                        }
                    }
                }
                if (hit == null && spatialIndex != null) {
                    hit = spatialIndex.findTapHit(touchMercX, touchMercY, hitRadiusMerc);
                }

                // Only trigger if long-pressed on empty ground (not on a bench)
                if (hit == null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    double lat = Bench.toDegreesLat(touchMercY);
                    double lon = Bench.toDegreesLon(touchMercX);
                    if (mapListener != null) {
                        mapListener.onMapLongPressed(lat, lon, touchX, touchY);
                    }
                }
            }
        });
    }

    private void cancelGestureDetector(MotionEvent event) {
        if (gestureDetector != null) {
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            gestureDetector.onTouchEvent(cancel);
            cancel.recycle();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        if (action == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true);
            if (System.currentTimeMillis() - lastDoubleTapTime > 400) {
                if (animator != null && animator.isRunning()) animator.cancel();
            }
            if (rotationAnimator != null && rotationAnimator.isRunning()) rotationAnimator.cancel();

            gestureHadMultiTouch = false;
            gestureHadMovement = false;
            touchDownX = event.getX();
            touchDownY = event.getY();
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            gestureHadMultiTouch = true;
            isScaling = true;
            cancelGestureDetector(event);
            if (pointerCount >= 2) {
                float p0x = event.getX(0);
                float p0y = event.getY(0);
                float p1x = event.getX(1);
                float p1y = event.getY(1);
                lastRotationAngle = Math.toDegrees(Math.atan2(p1y - p0y, p1x - p0x));
                isRotating = true;
            }
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (pointerCount == 1) {
                float dist = (float) Math.hypot(event.getX() - touchDownX, event.getY() - touchDownY);
                if (dist > 8f * density) {
                    gestureHadMovement = true;
                }
            } else {
                gestureHadMultiTouch = true;
            }

            if (pointerCount >= 2 && isRotating) {
                float p0x = event.getX(0);
                float p0y = event.getY(0);
                float p1x = event.getX(1);
                float p1y = event.getY(1);
                double currentAngle = Math.toDegrees(Math.atan2(p1y - p0y, p1x - p0x));
                double delta = currentAngle - lastRotationAngle;
                while (delta > 180.0) delta -= 360.0;
                while (delta < -180.0) delta += 360.0;

                if (Math.abs(delta) > 0.05) {
                    float oldRot = mapRotationDegrees;
                    mapRotationDegrees += (float) delta;
                    mapRotationDegrees = (mapRotationDegrees % 360f + 360f) % 360f;

                    // Snap to North if within 3.5 degrees
                    if (mapRotationDegrees < 3.5f || mapRotationDegrees > 356.5f) {
                        if (oldRot != 0f) {
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        mapRotationDegrees = 0f;
                    }
                    lastRotationAngle = currentAngle;
                    notifyRotationChanged();
                    invalidate();
                }
            }
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            lastPointerUpTime = System.currentTimeMillis();
            gestureHadMultiTouch = true;
            cancelGestureDetector(event);
            if (pointerCount <= 2) {
                isRotating = false;
            }
        }

        if (action == MotionEvent.ACTION_UP) {
            long now = System.currentTimeMillis();
            float upX = event.getX();
            float upY = event.getY();
            float slopPx = 32f * density;
            boolean recentlyPinched = (now - lastPointerUpTime < 500) || (now - lastScaleEndTime < 500);

            if (pointerCount == 1 && !gestureHadMultiTouch && !gestureHadMovement && !recentlyPinched
                    && (now - lastSingleTapUpTime < 320) && (Math.hypot(upX - lastSingleTapUpX, upY - lastSingleTapUpY) < slopPx)) {
                if (now - lastDoubleTapTime > 350 && !isScaling && !isRotating) {
                    lastDoubleTapTime = now;
                    double tapMercX = screenToMercX(upX, upY);
                    double tapMercY = screenToMercY(upX, upY);
                    animateToMerc(tapMercX, tapMercY, Math.min(MAX_SCALE, scale * 2.2f));
                }
                lastSingleTapUpTime = 0;
            } else {
                if (pointerCount == 1 && !gestureHadMultiTouch && !gestureHadMovement && !recentlyPinched) {
                    lastSingleTapUpTime = now;
                    lastSingleTapUpX = upX;
                    lastSingleTapUpY = upY;
                } else {
                    lastSingleTapUpTime = 0;
                }
            }
            isScaling = false;
            isRotating = false;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            isScaling = false;
            isRotating = false;
            lastSingleTapUpTime = 0;
        }

        scaleDetector.onTouchEvent(event);

        long now = System.currentTimeMillis();
        boolean recentlyPinched = (now - lastPointerUpTime < 500) || (now - lastScaleEndTime < 500);

        if (pointerCount == 1 && !isScaling && !isRotating && !scaleDetector.isInProgress() && !recentlyPinched && !gestureHadMultiTouch) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scroller.computeScrollOffset()) {
            int curX = scroller.getCurrX();
            int curY = scroller.getCurrY();
            int dx = curX - lastFlingX;
            int dy = curY - lastFlingY;
            lastFlingX = curX;
            lastFlingY = curY;

            if (mapRotationDegrees != 0f) {
                double rad = Math.toRadians(mapRotationDegrees);
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);
                float rotDx = dx * cos + dy * sin;
                float rotDy = -dx * sin + dy * cos;
                centerMercX -= rotDx / scale;
                centerMercY += rotDy / scale;
            } else {
                centerMercX -= dx / scale;
                centerMercY += dy / scale;
            }
            postInvalidateOnAnimation();
        }
    }

    /**
     * Ultra-fast binary map loader (Format Version 2 with street names).
     */
    private void loadVectorDataBinary() {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try (InputStream rawIn = getContext().getAssets().open("montreal_map.bin");
                 BufferedInputStream bis = new BufferedInputStream(rawIn, 65536);
                 DataInputStream in = new DataInputStream(bis)) {

                // 1. Header
                byte[] magic = new byte[4];
                in.readFully(magic);
                String magicStr = new String(magic, StandardCharsets.US_ASCII);
                if (!"BMAP".equals(magicStr)) {
                    throw new IllegalStateException("Invalid binary map header: " + magicStr);
                }
                int version = in.readInt();
                if (version != 2 && version != 3) {
                    throw new IllegalStateException("Expected map version 2 or 3, got: " + version);
                }

                // 2. String Tables
                String[] parks = readStringTable(in);
                String[] materials = readStringTable(in);
                String[] streets = readStringTable(in);
                String[] boroughs = (version >= 3) ? readStringTable(in) : new String[0];

                // 3. Geometry Layers
                List<GeometryLayer> loadedIsland = readGeometryLayers(in);
                List<GeometryLayer> loadedParks = readGeometryLayers(in);
                List<GeometryLayer> loadedMajor = readGeometryLayers(in);
                List<GeometryLayer> loadedMinor = readGeometryLayers(in);

                // 4. Benches (with street, borough, and address index)
                int benchCount = in.readInt();
                List<Bench> loadedBenches = new ArrayList<>(benchCount);
                for (int i = 0; i < benchCount; i++) {
                    float lat = in.readFloat();
                    float lon = in.readFloat();
                    float mx = in.readFloat();
                    float my = in.readFloat();
                    short pIdx = in.readShort();
                    short sIdx = in.readShort();
                    short bgIdx = (version >= 3) ? in.readShort() : -1;
                    int addrNum = (version >= 3) ? in.readUnsignedShort() : 0;
                    short mIdx = in.readShort();
                    byte backrest = in.readByte();
                    byte seats = in.readByte();

                    String park = (pIdx >= 0 && pIdx < parks.length) ? parks[pIdx] : "";
                    String street = (sIdx >= 0 && sIdx < streets.length) ? streets[sIdx] : "";
                    String borough = (bgIdx >= 0 && bgIdx < boroughs.length) ? boroughs[bgIdx] : "";
                    String material = (mIdx >= 0 && mIdx < materials.length) ? materials[mIdx] : "";

                    loadedBenches.add(new Bench(lat, lon, mx, my, park, street, borough, addrNum, material, backrest, seats));
                }

                SpatialBenchIndex newIndex = new SpatialBenchIndex(loadedBenches);
                SpatialStreetGrid loadedMajorGrid = new SpatialStreetGrid(loadedMajor, 12, 12, GRID_MIN_X, GRID_MAX_X, GRID_MIN_Y, GRID_MAX_Y);
                SpatialStreetGrid loadedMinorGrid = new SpatialStreetGrid(loadedMinor, 16, 16, GRID_MIN_X, GRID_MAX_X, GRID_MIN_Y, GRID_MAX_Y);

                synchronized (dataLock) {
                    islandPolys.clear();
                    islandPolys.addAll(loadedIsland);
                    parkPolys.clear();
                    parkPolys.addAll(loadedParks);
                    majorStreets.clear();
                    majorStreets.addAll(loadedMajor);
                    minorStreets.clear();
                    minorStreets.addAll(loadedMinor);
                    allBenches.clear();
                    allBenches.addAll(loadedBenches);
                    spatialIndex = newIndex;
                    majorGrid = loadedMajorGrid;
                    minorGrid = loadedMinorGrid;
                    isMapReady = true;
                }

                long elapsed = System.currentTimeMillis() - startTime;
                Log.d("BenchMap", "Binary map v2 loaded in " + elapsed + "ms ("
                        + majorStreets.size() + " major, " + minorStreets.size() + " minor, "
                        + loadedBenches.size() + " benches).");

                post(() -> {
                    if (mapListener != null) {
                        mapListener.onMapLoaded(loadedBenches.size());
                    }
                    invalidate();
                });

            } catch (Exception e) {
                Log.e("BenchMap", "Error loading binary vector map", e);
            }
        }, "MapLoaderThread").start();
    }

    private String[] readStringTable(DataInputStream in) throws Exception {
        short count = in.readShort();
        String[] table = new String[count];
        for (int i = 0; i < count; i++) {
            short len = in.readShort();
            byte[] strBytes = new byte[len];
            in.readFully(strBytes);
            table[i] = new String(strBytes, StandardCharsets.UTF_8);
        }
        return table;
    }

    private List<GeometryLayer> readGeometryLayers(DataInputStream in) throws Exception {
        int count = in.readInt();
        List<GeometryLayer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int ptCount = in.readInt();
            float minX = in.readFloat();
            float maxX = in.readFloat();
            float minY = in.readFloat();
            float maxY = in.readFloat();

            float[] coords = new float[ptCount];
            for (int j = 0; j < ptCount; j++) {
                coords[j] = in.readFloat();
            }
            list.add(new GeometryLayer(coords, minX, maxX, minY, maxY));
        }
        return list;
    }

    public void setMapListener(BenchMapListener listener) {
        this.mapListener = listener;
        if (isMapReady && listener != null) {
            listener.onMapLoaded(allBenches.size());
        }
    }

    public int getTotalCount() {
        return allBenches.size();
    }

    public void setUserLocation(Location loc) {
        this.userLocation = loc;
        postInvalidate();
    }

    public void setUserHeading(float degrees) {
        this.userHeadingDegrees = degrees;
        postInvalidate();
    }

    public void centerOnUser() {
        if (userLocation != null) {
            animateToCoords(userLocation.getLatitude(), userLocation.getLongitude(), 1800000f);
        }
    }

    public void animateToCoords(double lat, double lon, float targetScale) {
        animateToMerc(lonToMercatorX(lon), latToMercatorY(lat), targetScale);
    }

    public void animateToMerc(double targetMercX, double targetMercY, float targetScale) {
        if (animator != null && animator.isRunning()) animator.cancel();

        final double startX = centerMercX;
        final double startY = centerMercY;
        final float startScale = scale;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(380);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            centerMercX = startX + (targetMercX - startX) * f;
            centerMercY = startY + (targetMercY - startY) * f;
            scale = startScale + (targetScale - startScale) * f;
            invalidate();
        });
        animator.start();
    }

    public Bench findNearestBench() {
        SpatialBenchIndex index = this.spatialIndex;
        if (index == null) return null;

        double startLat = (userLocation != null) ? userLocation.getLatitude() : CENTER_LAT;
        double startLon = (userLocation != null) ? userLocation.getLongitude() : CENTER_LON;

        Bench nearest = index.findNearest(startLat, startLon);

        if (nearest != null) {
            this.selectedBench = nearest;
            animateToMerc(nearest.mercX, nearest.mercY, 2200000f);
            double dist = (userLocation != null) ?
                    computeDistance(userLocation.getLatitude(), userLocation.getLongitude(), nearest.lat, nearest.lon) : 0;
            if (mapListener != null) {
                mapListener.onBenchSelected(nearest, dist);
            }
        }
        return nearest;
    }

    public Bench selectRandomBench() {
        synchronized (dataLock) {
            if (allBenches.isEmpty()) return null;
            int randIdx = (int) (Math.random() * allBenches.size());
            Bench rand = allBenches.get(randIdx);
            this.selectedBench = rand;
            animateToMerc(rand.mercX, rand.mercY, 2200000f);
            double dist = (userLocation != null) ?
                    computeDistance(userLocation.getLatitude(), userLocation.getLongitude(), rand.lat, rand.lon) : 0;
            if (mapListener != null) {
                mapListener.onBenchSelected(rand, dist);
            }
            return rand;
        }
    }

    public void selectBench(Bench bench) {
        if (bench == null) return;
        this.selectedBench = bench;
        double dist = (userLocation != null) ?
                computeDistance(userLocation.getLatitude(), userLocation.getLongitude(), bench.lat, bench.lon) : 0;
        if (mapListener != null) {
            mapListener.onBenchSelected(bench, dist);
        }
        invalidate();
    }

    public void focusBench(Bench bench) {
        if (bench == null) return;
        this.selectedBench = bench;
        animateToMerc(bench.mercX, bench.mercY, 2200000f);
        double dist = (userLocation != null) ?
                computeDistance(userLocation.getLatitude(), userLocation.getLongitude(), bench.lat, bench.lon) : 0;
        if (mapListener != null) {
            mapListener.onBenchSelected(bench, dist);
        }
        invalidate();
    }

    public void setCustomBenches(List<Bench> benches) {
        synchronized (dataLock) {
            customBenches.clear();
            if (benches != null) {
                customBenches.addAll(benches);
            }
        }
        postInvalidate();
    }

    public void addCustomBench(Bench bench) {
        if (bench == null) return;
        synchronized (dataLock) {
            for (int i = 0; i < customBenches.size(); i++) {
                if (customBenches.get(i).getId().equals(bench.getId())) {
                    customBenches.set(i, bench);
                    postInvalidate();
                    return;
                }
            }
            customBenches.add(bench);
        }
        postInvalidate();
    }

    public void removeCustomBench(String benchId) {
        if (benchId == null) return;
        synchronized (dataLock) {
            for (int i = 0; i < customBenches.size(); i++) {
                if (customBenches.get(i).getId().equals(benchId)) {
                    customBenches.remove(i);
                    break;
                }
            }
        }
        postInvalidate();
    }

    public List<Bench> getCustomBenches() {
        synchronized (dataLock) {
            return new ArrayList<>(customBenches);
        }
    }

    public Bench findBenchById(String benchId) {
        if (benchId == null || benchId.isEmpty()) return null;
        synchronized (dataLock) {
            for (Bench b : customBenches) {
                if (b.getId().equals(benchId)) {
                    return b;
                }
            }
            for (Bench b : allBenches) {
                if (b.getId().equals(benchId)) {
                    return b;
                }
            }
        }
        return null;
    }

    public void deselectBench() {
        this.selectedBench = null;
        if (mapListener != null) {
            mapListener.onBenchDeselected();
        }
        invalidate();
    }

    private void handleTap(float touchX, float touchY) {
        if (touchY <= headerBottomPx) {
            return;
        }
        if (showFloatingCompass && compassBounds.contains(touchX, touchY)) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            onCompassTapped();
            return;
        }

        SpatialBenchIndex index = this.spatialIndex;
        double touchMercX = screenToMercX(touchX, touchY);
        double touchMercY = screenToMercY(touchX, touchY);

        float hitRadiusPx = 20f * density;
        double hitRadiusMerc = hitRadiusPx / scale;

        Bench hit = null;
        synchronized (dataLock) {
            for (int i = 0; i < customBenches.size(); i++) {
                Bench cb = customBenches.get(i);
                double dx = (cb.mercX - touchMercX) * scale;
                double dy = (cb.mercY - touchMercY) * scale;
                if (Math.hypot(dx, dy) <= hitRadiusPx) {
                    hit = cb;
                    break;
                }
            }
        }
        if (hit == null && index != null) {
            hit = index.findTapHit(touchMercX, touchMercY, hitRadiusMerc);
        }

        this.selectedBench = hit;
        if (hit != null) {
            double dist = (userLocation != null) ?
                    computeDistance(userLocation.getLatitude(), userLocation.getLongitude(), hit.lat, hit.lon) : 0;
            if (mapListener != null) {
                mapListener.onBenchSelected(hit, dist);
            }
        } else {
            if (mapListener != null) {
                mapListener.onBenchDeselected();
            }
        }
        invalidate();
    }

    private int getWaterColor(MapRenderStyle style, boolean isDarkMode) {
        if (isDarkMode) {
            switch (style) {
                case THIN_ARCHITECTURAL:
                    return Color.parseColor("#070A0F");
                case DASHED_CADASTRAL:
                    return Color.parseColor("#080B10");
                case DOTTED_MATRIX:
                    return Color.parseColor("#05080E");
                case ARTISTIC_BRUSH:
                    return Color.parseColor("#100F0D");
                case DOUBLE_CASING:
                    return Color.parseColor("#05070A");
                case BOLD_BAUHAUS:
                case DESATURATED_NOIR:
                case SWISS_CLEAN:
                default:
                    return DARK_WATER;
            }
        } else {
            switch (style) {
                case THIN_ARCHITECTURAL:
                    return Color.parseColor("#F0F4F8");
                case BOLD_BAUHAUS:
                    return Color.parseColor("#DCE2E8");
                case DASHED_CADASTRAL:
                    return Color.parseColor("#E8EEF5");
                case DOTTED_MATRIX:
                    return Color.parseColor("#E2E8F0");
                case ARTISTIC_BRUSH:
                    return Color.parseColor("#ECE5DA");
                case DOUBLE_CASING:
                    return Color.parseColor("#DCE5EE");
                case DESATURATED_NOIR:
                    return Color.parseColor("#E4E4E7");
                case SWISS_CLEAN:
                default:
                    return LIGHT_WATER;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        MapRenderStyle activeStyle = getActiveEffectiveStyle();

        // 1. Water Canvas Background
        canvas.drawColor(getWaterColor(activeStyle, isDarkMode));

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        double cX = centerMercX;
        double cY = centerMercY;
        float sc = scale;

        canvas.save();
        if (mapRotationDegrees != 0f) {
            canvas.rotate(mapRotationDegrees, halfW, halfH);
        }

        // Viewport bounds in Mercator space (expanded radius to ensure zero clipping during rotation)
        double viewRadius = Math.hypot(halfW + 60f * density, halfH + 60f * density) / sc;
        double viewMinX = cX - viewRadius;
        double viewMaxX = cX + viewRadius;
        double viewMinY = cY - viewRadius;
        double viewMaxY = cY + viewRadius;

        synchronized (dataLock) {
            // 2. Island Landmass
            for (int i = 0; i < islandPolys.size(); i++) {
                GeometryLayer poly = islandPolys.get(i);
                if (poly.maxX < viewMinX || poly.minX > viewMaxX || poly.maxY < viewMinY || poly.minY > viewMaxY) {
                    continue;
                }
                pathPoly.reset();
                float[] c = poly.coords;
                pathPoly.moveTo(halfW + (float) ((c[0] - cX) * sc), halfH - (float) ((c[1] - cY) * sc));
                for (int j = 2; j < c.length; j += 2) {
                    pathPoly.lineTo(halfW + (float) ((c[j] - cX) * sc), halfH - (float) ((c[j + 1] - cY) * sc));
                }
                pathPoly.close();
                canvas.drawPath(pathPoly, paintLand);
                canvas.drawPath(pathPoly, paintShoreline);
            }

            // 3. Parks & Green Spaces
            for (int i = 0; i < parkPolys.size(); i++) {
                GeometryLayer poly = parkPolys.get(i);
                if (poly.maxX < viewMinX || poly.minX > viewMaxX || poly.maxY < viewMinY || poly.minY > viewMaxY) {
                    continue;
                }
                pathPoly.reset();
                float[] c = poly.coords;
                pathPoly.moveTo(halfW + (float) ((c[0] - cX) * sc), halfH - (float) ((c[1] - cY) * sc));
                for (int j = 2; j < c.length; j += 2) {
                    pathPoly.lineTo(halfW + (float) ((c[j] - cX) * sc), halfH - (float) ((c[j + 1] - cY) * sc));
                }
                pathPoly.close();
                canvas.drawPath(pathPoly, paintPark);
                if (sc > 220000f) {
                    canvas.drawPath(pathPoly, paintParkBorder);
                }
            }

            // 4. Minor Streets (Spatially indexed batch lines at neighbourhood zoom)
            // Clutter-free island overview: only reveal minor streets when zooming into neighbourhoods
            if (sc > 340000f && minorGrid != null) {
                float minorWidth;
                switch (activeStyle) {
                    case THIN_ARCHITECTURAL:
                        minorWidth = (sc > 3000000f) ? (0.7f * density) : ((sc > 800000f) ? (0.42f * density) : (0.28f * density));
                        break;
                    case BOLD_BAUHAUS:
                        minorWidth = (sc > 3000000f) ? (7.0f * density) : ((sc > 800000f) ? (4.2f * density) : (2.8f * density));
                        break;
                    case DASHED_CADASTRAL:
                        minorWidth = (sc > 3000000f) ? (1.8f * density) : ((sc > 800000f) ? (1.2f * density) : (0.85f * density));
                        break;
                    case DOTTED_MATRIX:
                        minorWidth = (sc > 3000000f) ? (2.6f * density) : ((sc > 800000f) ? (1.8f * density) : (1.3f * density));
                        break;
                    case ARTISTIC_BRUSH:
                        minorWidth = (sc > 3000000f) ? (2.2f * density) : ((sc > 800000f) ? (1.4f * density) : (1.0f * density));
                        break;
                    case DESATURATED_NOIR:
                        minorWidth = (sc > 3000000f) ? (2.2f * density) : ((sc > 800000f) ? (1.3f * density) : (0.9f * density));
                        break;
                    case DOUBLE_CASING:
                        minorWidth = (sc > 3000000f) ? (1.8f * density) : ((sc > 800000f) ? (1.1f * density) : (0.8f * density));
                        break;
                    default:
                        minorWidth = (sc > 3000000f) ? (1.6f * density) : ((sc > 800000f) ? (1.0f * density) : (0.75f * density));
                        break;
                }
                paintStreetMinor.setStrokeWidth(minorWidth);
                minorGrid.drawVisible(canvas, lineBuffer, paintStreetMinor, viewMinX, viewMaxX, viewMinY, viewMaxY, halfW, halfH, cX, cY, sc, activeStyle, density);
            }

            // 5. Major Streets (Spatially indexed arterial lines across all zooms)
            if (majorGrid != null) {
                if (activeStyle == MapRenderStyle.DOUBLE_CASING && sc > 90000f) {
                    // Double casing: Pass 1 outer casing
                    float casingWidth = (sc > 3000000f) ? (8.0f * density) : ((sc > 600000f) ? (5.2f * density) : ((sc > 240000f) ? (3.8f * density) : (2.8f * density)));
                    paintStreetMajor.setStrokeWidth(casingWidth);
                    majorGrid.drawVisible(canvas, lineBuffer, paintStreetMajor, viewMinX, viewMaxX, viewMinY, viewMaxY, halfW, halfH, cX, cY, sc, MapRenderStyle.SWISS_CLEAN, density);

                    // Pass 2 inner core (matching land fill)
                    float coreWidth = (sc > 3000000f) ? (4.8f * density) : ((sc > 600000f) ? (3.0f * density) : ((sc > 240000f) ? (2.0f * density) : (1.4f * density)));
                    paintStreetCasingCore.setStrokeWidth(coreWidth);
                    majorGrid.drawVisible(canvas, lineBuffer, paintStreetCasingCore, viewMinX, viewMaxX, viewMinY, viewMaxY, halfW, halfH, cX, cY, sc, MapRenderStyle.SWISS_CLEAN, density);
                } else {
                    float majorWidth;
                    switch (activeStyle) {
                        case THIN_ARCHITECTURAL:
                            majorWidth = (sc > 3000000f) ? (1.5f * density) : ((sc > 600000f) ? (0.95f * density) : ((sc > 240000f) ? (0.65f * density) : (0.45f * density)));
                            break;
                        case BOLD_BAUHAUS:
                            majorWidth = (sc > 3000000f) ? (14.0f * density) : ((sc > 600000f) ? (8.5f * density) : ((sc > 240000f) ? (5.8f * density) : (4.0f * density)));
                            break;
                        case DASHED_CADASTRAL:
                            majorWidth = (sc > 3000000f) ? (4.2f * density) : ((sc > 600000f) ? (2.8f * density) : ((sc > 240000f) ? (2.0f * density) : (1.4f * density)));
                            break;
                        case DOTTED_MATRIX:
                            majorWidth = (sc > 3000000f) ? (4.8f * density) : ((sc > 600000f) ? (3.2f * density) : ((sc > 240000f) ? (2.4f * density) : (1.7f * density)));
                            break;
                        case ARTISTIC_BRUSH:
                            majorWidth = (sc > 3000000f) ? (5.0f * density) : ((sc > 600000f) ? (3.2f * density) : ((sc > 240000f) ? (2.2f * density) : (1.5f * density)));
                            break;
                        case DESATURATED_NOIR:
                            majorWidth = (sc > 3000000f) ? (6.0f * density) : ((sc > 600000f) ? (3.8f * density) : ((sc > 240000f) ? (2.6f * density) : (1.8f * density)));
                            break;
                        default:
                            majorWidth = (sc > 3000000f) ? (3.4f * density) : ((sc > 600000f) ? (2.2f * density) : ((sc > 240000f) ? (1.5f * density) : (1.1f * density)));
                            break;
                    }
                    paintStreetMajor.setStrokeWidth(majorWidth);
                    majorGrid.drawVisible(canvas, lineBuffer, paintStreetMajor, viewMinX, viewMaxX, viewMinY, viewMaxY, halfW, halfH, cX, cY, sc, activeStyle, density);
                }
            }

            // 6. Benches (Progressive Density LOD & Halo Badges)
            int step;
            float benchRadius;
            boolean drawHalo = false;

            if (sc < 120000f) {
                step = 16;
                benchRadius = 1.3f * density;
            } else if (sc < 260000f) {
                step = 6;
                benchRadius = 1.9f * density;
            } else if (sc < 520000f) {
                step = 2;
                benchRadius = 2.5f * density;
            } else if (sc < 1200000f) {
                step = 1;
                benchRadius = 3.4f * density;
                drawHalo = true;
            } else if (sc < 6000000f) {
                step = 1;
                benchRadius = 4.8f * density;
                drawHalo = true;
            } else {
                step = 1;
                benchRadius = 6.0f * density;
                drawHalo = true;
            }

            boolean isBauhaus = (activeStyle == MapRenderStyle.BOLD_BAUHAUS);
            if (isBauhaus) {
                benchRadius = benchRadius * 1.4f;
            } else if (activeStyle == MapRenderStyle.THIN_ARCHITECTURAL) {
                benchRadius = benchRadius * 0.8f;
            } else if (activeStyle == MapRenderStyle.DOTTED_MATRIX) {
                benchRadius = benchRadius * 1.15f;
            }

            visibleBenches.clear();
            if (spatialIndex != null) {
                double viewMinLon = Math.toDegrees(viewMinX);
                double viewMaxLon = Math.toDegrees(viewMaxX);
                double viewMinLat = Math.toDegrees(2.0 * Math.atan(Math.exp(viewMinY)) - Math.PI / 2.0);
                double viewMaxLat = Math.toDegrees(2.0 * Math.atan(Math.exp(viewMaxY)) - Math.PI / 2.0);

                spatialIndex.queryVisibleBenches(viewMinLat, viewMinLon, viewMaxLat, viewMaxLon, step, visibleBenches);
                int numBenches = visibleBenches.size();
                for (int i = 0; i < numBenches; i++) {
                    Bench b = visibleBenches.get(i);
                    float bx = halfW + (float) ((b.mercX - cX) * sc);
                    float by = halfH - (float) ((b.mercY - cY) * sc);
                    if (isBauhaus) {
                        if (drawHalo) {
                            canvas.drawRect(bx - benchRadius - 1.2f * density, by - benchRadius - 1.2f * density,
                                    bx + benchRadius + 1.2f * density, by + benchRadius + 1.2f * density, paintBenchHalo);
                        }
                        canvas.drawRect(bx - benchRadius, by - benchRadius, bx + benchRadius, by + benchRadius,
                                b.isInPark() ? paintBenchPark : paintBenchStreet);
                    } else {
                        if (drawHalo) {
                            canvas.drawCircle(bx, by, benchRadius + 1.2f * density, paintBenchHalo);
                        }
                        canvas.drawCircle(bx, by, benchRadius, b.isInPark() ? paintBenchPark : paintBenchStreet);
                    }
                }
            }

            // 6.5. Meetup Candidate Benches (Luminous Amber Halos)
            if (!meetupBenches.isEmpty()) {
                float mHaloR = benchRadius + 3.8f * density;
                for (int i = 0; i < meetupBenches.size(); i++) {
                    Bench mb = meetupBenches.get(i);
                    float mx = halfW + (float) ((mb.mercX - cX) * sc);
                    float my = halfH - (float) ((mb.mercY - cY) * sc);
                    canvas.drawCircle(mx, my, mHaloR + 1.2f * density, paintBenchHalo);
                    canvas.drawCircle(mx, my, mHaloR, paintMeetupBenchHalo);
                    canvas.drawCircle(mx, my, benchRadius + 0.8f * density, paintMeetupBenchCore);
                }
            }

            // 6.6. Geodesic Connection Line between User and Friend
            if (userLocation != null && friendLat != null && friendLon != null) {
                float ux = halfW + (float) ((lonToMercatorX(userLocation.getLongitude()) - cX) * sc);
                float uy = halfH - (float) ((latToMercatorY(userLocation.getLatitude()) - cY) * sc);
                float fx = halfW + (float) ((lonToMercatorX(friendLon) - cX) * sc);
                float fy = halfH - (float) ((latToMercatorY(friendLat) - cY) * sc);
                canvas.drawLine(ux, uy, fx, fy, paintMeetupLine);
            }

            // 6.7. Custom User Benches (Losange / Diamond ◆ Shape)
            if (!customBenches.isEmpty()) {
                float diamondRadius = Math.max(benchRadius * 1.45f, 5.2f * density);
                for (int i = 0; i < customBenches.size(); i++) {
                    Bench cb = customBenches.get(i);
                    float cxBench = halfW + (float) ((cb.mercX - cX) * sc);
                    float cyBench = halfH - (float) ((cb.mercY - cY) * sc);

                    if (cxBench < -30f || cxBench > w + 30f || cyBench < -30f || cyBench > h + 30f) {
                        continue;
                    }

                    customDiamondPath.reset();
                    customDiamondPath.moveTo(cxBench, cyBench - diamondRadius);
                    customDiamondPath.lineTo(cxBench + diamondRadius, cyBench);
                    customDiamondPath.lineTo(cxBench, cyBench + diamondRadius);
                    customDiamondPath.lineTo(cxBench - diamondRadius, cyBench);
                    customDiamondPath.close();

                    if (drawHalo) {
                        canvas.drawPath(customDiamondPath, paintCustomBenchHalo);
                    }
                    canvas.drawPath(customDiamondPath, paintCustomBenchFill);
                    canvas.drawPath(customDiamondPath, paintCustomBenchStroke);

                    // Inner center dot / core
                    canvas.drawCircle(cxBench, cyBench, diamondRadius * 0.35f, paintCustomBenchCenter);
                }
            }

            // 7. Selected Bench Highlight (Swiss Concentric Rings or Concentric Diamonds)
            if (selectedBench != null) {
                float bx = halfW + (float) ((selectedBench.mercX - cX) * sc);
                float by = halfH - (float) ((selectedBench.mercY - cY) * sc);

                if (selectedBench.isCustom) {
                    float diamondRadius = Math.max(benchRadius * 1.45f, 5.2f * density);
                    float selSize = diamondRadius + 7.5f * density;
                    float gapSize = diamondRadius + 4.0f * density;

                    Path dOuter = new Path();
                    dOuter.moveTo(bx, by - selSize);
                    dOuter.lineTo(bx + selSize, by);
                    dOuter.lineTo(bx, by + selSize);
                    dOuter.lineTo(bx - selSize, by);
                    dOuter.close();
                    canvas.drawPath(dOuter, paintBenchSelected);

                    Path dGap = new Path();
                    dGap.moveTo(bx, by - gapSize);
                    dGap.lineTo(bx + gapSize, by);
                    dGap.lineTo(bx, by + gapSize);
                    dGap.lineTo(bx - gapSize, by);
                    dGap.close();
                    canvas.drawPath(dGap, paintBenchSelectedGap);

                    Path dCore = new Path();
                    dCore.moveTo(bx, by - diamondRadius);
                    dCore.lineTo(bx + diamondRadius, by);
                    dCore.lineTo(bx, by + diamondRadius);
                    dCore.lineTo(bx - diamondRadius, by);
                    dCore.close();
                    canvas.drawPath(dCore, paintBenchSelectedCore);
                } else if (isBauhaus) {
                    float selRadius = Math.max(benchRadius, 4.5f * density);
                    float outer = selRadius + 8.0f * density;
                    float gap = selRadius + 4.5f * density;
                    float core = selRadius + 1.5f * density;
                    canvas.drawRect(bx - outer, by - outer, bx + outer, by + outer, paintBenchSelected);
                    canvas.drawRect(bx - gap, by - gap, bx + gap, by + gap, paintBenchSelectedGap);
                    canvas.drawRect(bx - core, by - core, bx + core, by + core, paintBenchSelectedCore);
                } else {
                    float selRadius = Math.max(benchRadius, 4.0f * density);
                    canvas.drawCircle(bx, by, selRadius + 8.0f * density, paintBenchSelected);
                    canvas.drawCircle(bx, by, selRadius + 4.5f * density, paintBenchSelectedGap);
                    canvas.drawCircle(bx, by, selRadius + 1.5f * density, paintBenchSelectedCore);
                }
            }
        }

        // 8. User Precision Swiss Pin & Compass Heading Cone
        if (userLocation != null) {
            float ux = halfW + (float) ((lonToMercatorX(userLocation.getLongitude()) - cX) * sc);
            float uy = halfH - (float) ((latToMercatorY(userLocation.getLatitude()) - cY) * sc);
            drawUserPin(canvas, ux, uy, userLocation.hasAccuracy() ? userLocation.getAccuracy() : 0);
        }

        // 9. Friend Precision Cobalt Pin & Privacy Blur Circle
        if (friendLat != null && friendLon != null) {
            float fx = halfW + (float) ((lonToMercatorX(friendLon) - cX) * sc);
            float fy = halfH - (float) ((latToMercatorY(friendLat) - cY) * sc);
            drawFriendPin(canvas, fx, fy, friendBlurMeters);
        }

        canvas.restore();

        // 10. Swiss Minimal Compass Rose (if floating mode is enabled)
        if (showFloatingCompass) {
            drawSwissCompass(canvas, w, h);
        }
    }

    private void drawUserPin(Canvas canvas, float ux, float uy, float accuracyMeters) {
        // 1. Accuracy Circle
        if (accuracyMeters > 0 && scale > 180000f) {
            double metersPerMercRad = 6371000.0 * Math.cos(Math.toRadians(CENTER_LAT));
            float radiusPx = (float) ((accuracyMeters / metersPerMercRad) * scale);
            if (radiusPx > 8f * density && radiusPx < 400f * density) {
                canvas.drawCircle(ux, uy, radiusPx, paintAccuracyFill);
                canvas.drawCircle(ux, uy, radiusPx, paintAccuracyStroke);
            }
        }

        // 2. Heading Orientation Cone
        if (userHeadingDegrees >= 0) {
            float coneRadius = 42f * density;
            headingPath.reset();
            headingPath.moveTo(ux, uy);
            headingArcRect.set(ux - coneRadius, uy - coneRadius, ux + coneRadius, uy + coneRadius);
            headingPath.arcTo(headingArcRect, userHeadingDegrees - 90f - 24f, 48f);
            headingPath.close();
            canvas.drawPath(headingPath, paintHeadingCone);
        }

        // 3. Ground Shadow & 4. Swiss Teardrop Pin Geometry
        // Counter-rotate by -mapRotationDegrees around the pin anchor point (ux, uy) so the pin always stands upright
        canvas.save();
        if (mapRotationDegrees != 0f) {
            canvas.rotate(-mapRotationDegrees, ux, uy);
        }

        RectF shadowRect = new RectF(
                ux - 8.5f * density,
                uy - 2.5f * density,
                ux + 8.5f * density,
                uy + 3.5f * density
        );
        canvas.drawOval(shadowRect, paintPinShadow);

        float headCenterY = uy - 22f * density;
        float headRadius = 8.5f * density;

        pinPath.reset();
        pinPath.moveTo(ux, uy);

        pinPath.cubicTo(
                ux - 2.5f * density, uy - 8f * density,
                ux - headRadius, headCenterY + 4f * density,
                ux - headRadius, headCenterY
        );

        pinPath.arcTo(
                ux - headRadius, headCenterY - headRadius,
                ux + headRadius, headCenterY + headRadius,
                180f, 180f, false
        );

        pinPath.cubicTo(
                ux + headRadius, headCenterY + 4f * density,
                ux + 2.5f * density, uy - 8f * density,
                ux, uy
        );
        pinPath.close();

        canvas.drawPath(pinPath, paintPinFill);
        canvas.drawPath(pinPath, paintPinStroke);
        canvas.drawCircle(ux, headCenterY, 3.0f * density, paintPinDot);
        canvas.restore();
    }

    private void drawFriendPin(Canvas canvas, float fx, float fy, float blurMeters) {
        // 1. Privacy Blur Circle (if enabled)
        if (blurMeters > 0 && scale > 120000f) {
            double metersPerMercRad = 6371000.0 * Math.cos(Math.toRadians(CENTER_LAT));
            float radiusPx = (float) ((blurMeters / metersPerMercRad) * scale);
            if (radiusPx > 8f * density && radiusPx < 500f * density) {
                canvas.drawCircle(fx, fy, radiusPx, paintFriendBlurFill);
                canvas.drawCircle(fx, fy, radiusPx, paintFriendBlurStroke);
            }
        }

        // 2. Ground Shadow & 3. Swiss Teardrop Pin Geometry (Cobalt Blue for Friend)
        // Counter-rotate by -mapRotationDegrees around the pin anchor point (fx, fy) so the pin always stands upright
        canvas.save();
        if (mapRotationDegrees != 0f) {
            canvas.rotate(-mapRotationDegrees, fx, fy);
        }

        RectF shadowRect = new RectF(
                fx - 8.5f * density,
                fy - 2.5f * density,
                fx + 8.5f * density,
                fy + 3.5f * density
        );
        canvas.drawOval(shadowRect, paintPinShadow);

        float headCenterY = fy - 22f * density;
        float headRadius = 8.5f * density;

        friendPinPath.reset();
        friendPinPath.moveTo(fx, fy);
        friendPinPath.cubicTo(
                fx - 2.5f * density, fy - 8f * density,
                fx - headRadius, headCenterY + 4f * density,
                fx - headRadius, headCenterY
        );
        friendPinPath.arcTo(
                fx - headRadius, headCenterY - headRadius,
                fx + headRadius, headCenterY + headRadius,
                180f, 180f, false
        );
        friendPinPath.cubicTo(
                fx + headRadius, headCenterY + 4f * density,
                fx + 2.5f * density, fy - 8f * density,
                fx, fy
        );
        friendPinPath.close();

        canvas.drawPath(friendPinPath, paintFriendPinFill);
        canvas.drawPath(friendPinPath, paintFriendPinStroke);
        canvas.drawCircle(fx, headCenterY, 3.0f * density, paintPinDot);
        canvas.restore();
    }

    public void setMeetup(double fLat, double fLon, int fBlurMeters, List<Bench> halfwayList) {
        this.friendLat = fLat;
        this.friendLon = fLon;
        this.friendBlurMeters = fBlurMeters;
        this.meetupBenches.clear();
        if (halfwayList != null) {
            this.meetupBenches.addAll(halfwayList);
        }
        postInvalidate();
    }

    public void clearMeetup() {
        this.friendLat = null;
        this.friendLon = null;
        this.friendBlurMeters = 0;
        this.meetupBenches.clear();
        postInvalidate();
    }

    public boolean isMeetupActive() {
        return friendLat != null && friendLon != null;
    }

    public Double getFriendLat() {
        return friendLat;
    }

    public Double getFriendLon() {
        return friendLon;
    }

    public List<Bench> getMeetupBenches() {
        return new ArrayList<>(meetupBenches);
    }

    public double getCenterLat() {
        return Bench.toDegreesLat(centerMercY);
    }

    public double getCenterLon() {
        return Bench.toDegreesLon(centerMercX);
    }

    public List<Bench> getAllBenches() {
        synchronized (dataLock) {
            return new ArrayList<>(allBenches);
        }
    }

    public void fitBounds(double lat1, double lon1, double lat2, double lon2) {
        double mX1 = lonToMercatorX(lon1);
        double mY1 = latToMercatorY(lat1);
        double mX2 = lonToMercatorX(lon2);
        double mY2 = latToMercatorY(lat2);

        double targetCX = (mX1 + mX2) * 0.5;
        double targetCY = (mY1 + mY2) * 0.5;

        double dX = Math.abs(mX1 - mX2);
        double dY = Math.abs(mY1 - mY2);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float targetScaleX = (float) ((w * 0.55) / Math.max(0.0001, dX));
        float targetScaleY = (float) ((h * 0.40) / Math.max(0.0001, dY));
        float targetScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE * 0.5f, Math.min(targetScaleX, targetScaleY)));

        animateToMerc(targetCX, targetCY, targetScale);
    }

    private float screenX(double mercX) {
        return (getWidth() * 0.5f) + (float) ((mercX - centerMercX) * scale);
    }

    private float screenY(double mercY) {
        return (getHeight() * 0.5f) - (float) ((mercY - centerMercY) * scale);
    }

    public double screenToMercX(float px, float py) {
        float halfW = getWidth() * 0.5f;
        float halfH = getHeight() * 0.5f;
        float x = px - halfW;
        float y = py - halfH;
        if (mapRotationDegrees != 0f) {
            double rad = Math.toRadians(mapRotationDegrees);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float x0 = x * cos + y * sin;
            return centerMercX + x0 / scale;
        }
        return centerMercX + x / scale;
    }

    public double screenToMercY(float px, float py) {
        float halfW = getWidth() * 0.5f;
        float halfH = getHeight() * 0.5f;
        float x = px - halfW;
        float y = py - halfH;
        if (mapRotationDegrees != 0f) {
            double rad = Math.toRadians(mapRotationDegrees);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float y0 = -x * sin + y * cos;
            return centerMercY - y0 / scale;
        }
        return centerMercY - y / scale;
    }

    private double screenToMercX(float px) {
        return screenToMercX(px, getHeight() * 0.5f);
    }

    private double screenToMercY(float py) {
        return screenToMercY(getWidth() * 0.5f, py);
    }

    public void setCompassTopMargin(float topPx) {
        this.compassTopPx = topPx;
        invalidate();
    }

    private void drawSwissCompass(Canvas canvas, int w, int h) {
        float radius = 21f * density;
        float cx = w - 16f * density - radius;
        float cy = (compassTopPx > 0) ? (compassTopPx + radius) : (160f * density);

        compassBounds.set(cx - radius - 8f * density, cy - radius - 8f * density,
                cx + radius + 8f * density, cy + radius + 8f * density);

        // Ground subtle shadow
        RectF shadowRect = new RectF(
                cx - radius - 1f * density,
                cy - radius + 1f * density,
                cx + radius + 1f * density,
                cy + radius + 3f * density
        );
        canvas.drawOval(shadowRect, paintPinShadow);

        // Background & stroke
        canvas.drawCircle(cx, cy, radius, paintCompassBg);
        canvas.drawCircle(cx, cy, radius, paintCompassStroke);

        // Compass needle rotated with the map
        canvas.save();
        canvas.rotate(mapRotationDegrees, cx, cy);

        float needleLen = radius * 0.65f;
        float needleW = 4.2f * density;

        // North needle (Swiss Red triangle)
        compassNorthPath.reset();
        compassNorthPath.moveTo(cx, cy - needleLen);
        compassNorthPath.lineTo(cx - needleW, cy);
        compassNorthPath.lineTo(cx + needleW, cy);
        compassNorthPath.close();
        canvas.drawPath(compassNorthPath, paintCompassNorthNeedle);

        // South needle (Muted slate triangle)
        compassSouthPath.reset();
        compassSouthPath.moveTo(cx, cy + needleLen);
        compassSouthPath.lineTo(cx - needleW, cy);
        compassSouthPath.lineTo(cx + needleW, cy);
        compassSouthPath.close();
        canvas.drawPath(compassSouthPath, paintCompassSouthNeedle);

        // Center pivot dot
        canvas.drawCircle(cx, cy, 2.5f * density, paintCompassBg);
        canvas.drawCircle(cx, cy, 1.4f * density, paintCompassNorthNeedle);

        canvas.restore();
    }

    /**
     * Pressing on the Swiss compass zooms in and resets the map orientation to True North.
     */
    public void onCompassTapped() {
        resetRotationToNorth();
        float targetScale = Math.min(MAX_SCALE, scale * 2.0f);
        animateToMerc(centerMercX, centerMercY, targetScale);
    }

    public void resetRotationToNorth() {
        if (rotationAnimator != null && rotationAnimator.isRunning()) {
            rotationAnimator.cancel();
        }
        float startRot = mapRotationDegrees;
        if (startRot == 0f) return;

        float diff = (((-startRot % 360f) + 540f) % 360f) - 180f;
        float targetRot = startRot + diff;

        rotationAnimator = ValueAnimator.ofFloat(startRot, targetRot);
        rotationAnimator.setDuration(280);
        rotationAnimator.setInterpolator(new DecelerateInterpolator());
        rotationAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            mapRotationDegrees = (val % 360f + 360f) % 360f;
            if (Math.abs(mapRotationDegrees) < 0.2f || Math.abs(mapRotationDegrees - 360f) < 0.2f) {
                mapRotationDegrees = 0f;
            }
            notifyRotationChanged();
            invalidate();
        });
        rotationAnimator.start();
    }

    public float getMapRotation() {
        return mapRotationDegrees;
    }

    public void setMapRotation(float rotationDegrees) {
        this.mapRotationDegrees = (rotationDegrees % 360f + 360f) % 360f;
        notifyRotationChanged();
        invalidate();
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float newScale) {
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        invalidate();
    }

    public static double lonToMercatorX(double lon) {
        return Math.toRadians(lon);
    }

    public static double latToMercatorY(double lat) {
        double rad = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, lat)));
        return Math.log(Math.tan(Math.PI / 4.0 + rad / 2.0));
    }

    public static double computeDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // Earth radius in meters
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) +
                Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
