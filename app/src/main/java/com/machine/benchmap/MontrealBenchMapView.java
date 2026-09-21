package com.machine.benchmap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
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
                                float halfW, float halfH, double cX, double cY, float sc) {
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
                            if (bufIdx + 4 > maxCap) {
                                canvas.drawLines(lineBuffer, 0, bufIdx, paint);
                                bufIdx = 0;
                            }
                            lineBuffer[bufIdx++] = halfW + (float) ((coords[j] - cX) * sc);
                            lineBuffer[bufIdx++] = halfH - (float) ((coords[j + 1] - cY) * sc);
                            lineBuffer[bufIdx++] = halfW + (float) ((coords[j + 2] - cX) * sc);
                            lineBuffer[bufIdx++] = halfH - (float) ((coords[j + 3] - cY) * sc);
                        }
                    }
                }
            }

            if (bufIdx > 0) {
                canvas.drawLines(lineBuffer, 0, bufIdx, paint);
            }
        }
    }

    // Colors - Refined Architectural Swiss Palette
    private static final int COLOR_WATER = Color.parseColor("#EBF1F6");         // Crisp architectural Nordic water
    private static final int COLOR_LAND = Color.parseColor("#FFFFFF");          // Pure clean landmass
    private static final int COLOR_SHORELINE = Color.parseColor("#CBD5E1");     // Subtle hairline perimeter
    private static final int COLOR_PARK = Color.parseColor("#EAF5EC");         // Serene organic sage green
    private static final int COLOR_PARK_BORDER = Color.parseColor("#A7D7B5");   // Park boundary hairline
    private static final int COLOR_STREET_MAJOR = Color.parseColor("#475569");  // Slate 600 - elegant arterial lines
    private static final int COLOR_STREET_MINOR = Color.parseColor("#CBD5E1");  // Slate 300 - whisper hairline residential
    private static final int COLOR_BENCH_STREET = Color.parseColor("#1E293B");  // Swiss charcoal
    private static final int COLOR_BENCH_PARK = Color.parseColor("#15803D");    // Forest emerald
    private static final int COLOR_SWISS_RED = Color.parseColor("#DE3831");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");

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

    // User Pin & Heading Cone Paints
    private final Paint paintPinFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPinStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPinDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPinShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAccuracyFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAccuracyStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHeadingCone = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Reusable Paths & Rects
    private final Path pathPoly = new Path();
    private final Path pinPath = new Path();
    private final Path headingPath = new Path();
    private final RectF headingArcRect = new RectF();

    // Reusable line batch buffer for hardware drawLines (avoids Path allocations)
    private final float[] lineBuffer = new float[8192];

    // Geographic center of Montreal (Mount Royal)
    public static final double CENTER_LAT = 45.50884;
    public static final double CENTER_LON = -73.58781;
    private double centerMercX = lonToMercatorX(CENTER_LON);
    private double centerMercY = latToMercatorY(CENTER_LAT);

    // Zoom & Limits
    private static final float MIN_SCALE = 55000f;     // Whole metropolitan island
    private static final float MAX_SCALE = 9000000f;   // Sub-meter street resolution
    private float scale = 220000f;                     // Default Montreal overview
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

    // Vector Data & Spatial Index
    private final Object dataLock = new Object();
    private final List<GeometryLayer> islandPolys = new ArrayList<>();
    private final List<GeometryLayer> parkPolys = new ArrayList<>();
    private final List<GeometryLayer> majorStreets = new ArrayList<>();
    private final List<GeometryLayer> minorStreets = new ArrayList<>();
    private final List<Bench> allBenches = new ArrayList<>();
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
        paintLand.setColor(COLOR_LAND);
        paintLand.setStyle(Paint.Style.FILL);

        paintShoreline.setColor(COLOR_SHORELINE);
        paintShoreline.setStyle(Paint.Style.STROKE);
        paintShoreline.setStrokeWidth(1.1f * density);

        // Parks & Green Spaces
        paintPark.setColor(COLOR_PARK);
        paintPark.setStyle(Paint.Style.FILL);

        paintParkBorder.setColor(COLOR_PARK_BORDER);
        paintParkBorder.setStyle(Paint.Style.STROKE);
        paintParkBorder.setStrokeWidth(0.75f * density);

        // Major Streets (Clean Slate 600 - BUTT & MITER for high FPS GPU throughput)
        paintStreetMajor.setColor(COLOR_STREET_MAJOR);
        paintStreetMajor.setStyle(Paint.Style.STROKE);
        paintStreetMajor.setStrokeWidth(1.5f * density);
        paintStreetMajor.setStrokeCap(Paint.Cap.BUTT);
        paintStreetMajor.setStrokeJoin(Paint.Join.MITER);

        // Minor Streets (Subtle Hairline Slate 300 - BUTT & MITER for high FPS GPU throughput)
        paintStreetMinor.setColor(COLOR_STREET_MINOR);
        paintStreetMinor.setStyle(Paint.Style.STROKE);
        paintStreetMinor.setStrokeWidth(0.75f * density);
        paintStreetMinor.setStrokeCap(Paint.Cap.BUTT);
        paintStreetMinor.setStrokeJoin(Paint.Join.MITER);

        // Benches
        paintBenchStreet.setColor(COLOR_BENCH_STREET);
        paintBenchStreet.setStyle(Paint.Style.FILL);

        paintBenchPark.setColor(COLOR_BENCH_PARK);
        paintBenchPark.setStyle(Paint.Style.FILL);

        paintBenchHalo.setColor(COLOR_WHITE);
        paintBenchHalo.setStyle(Paint.Style.FILL);

        // Selected Bench Rings
        paintBenchSelected.setColor(COLOR_SWISS_RED);
        paintBenchSelected.setStyle(Paint.Style.STROKE);
        paintBenchSelected.setStrokeWidth(2.4f * density);

        paintBenchSelectedGap.setColor(COLOR_LAND);
        paintBenchSelectedGap.setStyle(Paint.Style.STROKE);
        paintBenchSelectedGap.setStrokeWidth(2.0f * density);

        paintBenchSelectedCore.setColor(COLOR_SWISS_RED);
        paintBenchSelectedCore.setStyle(Paint.Style.FILL);

        // User Swiss Pin
        paintPinFill.setColor(COLOR_SWISS_RED);
        paintPinFill.setStyle(Paint.Style.FILL);

        paintPinStroke.setColor(COLOR_WHITE);
        paintPinStroke.setStyle(Paint.Style.STROKE);
        paintPinStroke.setStrokeWidth(1.8f * density);

        paintPinDot.setColor(COLOR_WHITE);
        paintPinDot.setStyle(Paint.Style.FILL);

        paintPinShadow.setColor(Color.argb(55, 30, 41, 59));
        paintPinShadow.setStyle(Paint.Style.FILL);

        paintAccuracyFill.setColor(COLOR_SWISS_RED);
        paintAccuracyFill.setStyle(Paint.Style.FILL);
        paintAccuracyFill.setAlpha(20);

        paintAccuracyStroke.setColor(COLOR_SWISS_RED);
        paintAccuracyStroke.setStyle(Paint.Style.STROKE);
        paintAccuracyStroke.setStrokeWidth(1.0f * density);
        paintAccuracyStroke.setAlpha(65);

        paintHeadingCone.setColor(COLOR_SWISS_RED);
        paintHeadingCone.setStyle(Paint.Style.FILL);
        paintHeadingCone.setAlpha(35);

        initGestures();
        loadVectorDataBinary();
    }

    private void initGestures() {
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                scroller.forceFinished(true);
                if (animator != null && animator.isRunning()) animator.cancel();
                lastFocusX = detector.getFocusX();
                lastFocusY = detector.getFocusY();
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
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
                if (animator != null && animator.isRunning()) animator.cancel();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // Suppress single-finger scroll jumps when multiple touches or right after scaling
                if (isScaling || scaleDetector.isInProgress() || (e2 != null && e2.getPointerCount() > 1)) {
                    return false;
                }
                long now = System.currentTimeMillis();
                if (now - lastPointerUpTime < 100 || now - lastScaleEndTime < 100) {
                    return false;
                }

                centerMercX += distanceX / scale;
                centerMercY -= distanceY / scale;
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isScaling || scaleDetector.isInProgress() || (e2 != null && e2.getPointerCount() > 1)) {
                    return false;
                }
                long now = System.currentTimeMillis();
                if (now - lastPointerUpTime < 100 || now - lastScaleEndTime < 100) {
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
                if (!isScaling) {
                    handleTap(e.getX(), e.getY());
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!isScaling) {
                    double tapMercX = screenToMercX(e.getX());
                    double tapMercY = screenToMercY(e.getY());
                    animateToMerc(tapMercX, tapMercY, Math.min(MAX_SCALE, scale * 2.2f));
                }
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true);
            if (animator != null && animator.isRunning()) animator.cancel();
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            isScaling = true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            lastPointerUpTime = System.currentTimeMillis();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isScaling = false;
        }

        scaleDetector.onTouchEvent(event);

        long now = System.currentTimeMillis();
        boolean recentlyPinched = (now - lastPointerUpTime < 100) || (now - lastScaleEndTime < 100);

        if (event.getPointerCount() == 1 && !isScaling && !scaleDetector.isInProgress() && !recentlyPinched) {
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

            centerMercX -= dx / scale;
            centerMercY += dy / scale;
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

    public void deselectBench() {
        this.selectedBench = null;
        if (mapListener != null) {
            mapListener.onBenchDeselected();
        }
        invalidate();
    }

    private void handleTap(float touchX, float touchY) {
        SpatialBenchIndex index = this.spatialIndex;
        if (index == null) return;

        double touchMercX = screenToMercX(touchX);
        double touchMercY = screenToMercY(touchY);

        float hitRadiusPx = 36f * density;
        double hitRadiusMerc = hitRadiusPx / scale;

        Bench hit = index.findTapHit(touchMercX, touchMercY, hitRadiusMerc);

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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Water Canvas Background
        canvas.drawColor(COLOR_WATER);

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        double cX = centerMercX;
        double cY = centerMercY;
        float sc = scale;

        // Viewport bounds in Mercator space
        double viewMinX = cX + (-40f - halfW) / sc;
        double viewMaxX = cX + (w + 40f - halfW) / sc;
        double viewMinY = cY - (h + 40f - halfH) / sc;
        double viewMaxY = cY - (-40f - halfH) / sc;

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
                float minorWidth = (sc > 800000f) ? (1.0f * density) : (0.75f * density);
                paintStreetMinor.setStrokeWidth(minorWidth);
                minorGrid.drawVisible(canvas, lineBuffer, paintStreetMinor, viewMinX, viewMaxX, viewMinY, viewMaxY, halfW, halfH, cX, cY, sc);
            }

            // 5. Major Streets (Spatially indexed arterial lines across all zooms)
            if (majorGrid != null) {
                float majorWidth = (sc > 600000f) ? (2.2f * density)
                        : ((sc > 240000f) ? (1.5f * density) : (1.1f * density));
                paintStreetMajor.setStrokeWidth(majorWidth);
                majorGrid.drawVisible(canvas, lineBuffer, paintStreetMajor, viewMinX, viewMaxX, viewMinY, viewMaxY, halfW, halfH, cX, cY, sc);
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
            } else {
                step = 1;
                benchRadius = 4.8f * density;
                drawHalo = true;
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
                    if (drawHalo) {
                        canvas.drawCircle(bx, by, benchRadius + 1.2f * density, paintBenchHalo);
                    }
                    canvas.drawCircle(bx, by, benchRadius, b.isInPark() ? paintBenchPark : paintBenchStreet);
                }
            }

            // 7. Selected Bench Highlight (Swiss Concentric Rings)
            if (selectedBench != null) {
                float bx = halfW + (float) ((selectedBench.mercX - cX) * sc);
                float by = halfH - (float) ((selectedBench.mercY - cY) * sc);
                float selRadius = Math.max(benchRadius, 4.0f * density);

                canvas.drawCircle(bx, by, selRadius + 8.0f * density, paintBenchSelected);
                canvas.drawCircle(bx, by, selRadius + 4.5f * density, paintBenchSelectedGap);
                canvas.drawCircle(bx, by, selRadius + 1.5f * density, paintBenchSelectedCore);
            }
        }

        // 8. User Precision Swiss Pin & Compass Heading Cone
        if (userLocation != null) {
            float ux = halfW + (float) ((lonToMercatorX(userLocation.getLongitude()) - cX) * sc);
            float uy = halfH - (float) ((latToMercatorY(userLocation.getLatitude()) - cY) * sc);
            drawUserPin(canvas, ux, uy, userLocation.hasAccuracy() ? userLocation.getAccuracy() : 0);
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

        // 3. Ground Shadow
        RectF shadowRect = new RectF(
                ux - 8.5f * density,
                uy - 2.5f * density,
                ux + 8.5f * density,
                uy + 3.5f * density
        );
        canvas.drawOval(shadowRect, paintPinShadow);

        // 4. Swiss Teardrop Pin Geometry
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
    }

    private float screenX(double mercX) {
        return (getWidth() * 0.5f) + (float) ((mercX - centerMercX) * scale);
    }

    private float screenY(double mercY) {
        return (getHeight() * 0.5f) - (float) ((mercY - centerMercY) * scale);
    }

    private double screenToMercX(float px) {
        return centerMercX + (px - getWidth() * 0.5f) / scale;
    }

    private double screenToMercY(float py) {
        return centerMercY - (py - getHeight() * 0.5f) / scale;
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
