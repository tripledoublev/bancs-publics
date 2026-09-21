package com.machine.benchmap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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

        public GeometryLayer(float[] coords, float minX, float maxX, float minY, float maxY) {
            this.coords = coords;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    // Colors - Refined Architectural Swiss Palette
    private static final int COLOR_WATER = Color.parseColor("#E2E8F0");
    private static final int COLOR_LAND = Color.parseColor("#FFFFFF");
    private static final int COLOR_SHORELINE = Color.parseColor("#0F172A");
    private static final int COLOR_PARK = Color.parseColor("#EBF5EE");         // Serene organic sage green
    private static final int COLOR_PARK_BORDER = Color.parseColor("#94A3B8");   // Park boundary hairline
    private static final int COLOR_STREET_MAJOR = Color.parseColor("#334155");  // Crisp slate 700
    private static final int COLOR_STREET_MINOR = Color.parseColor("#94A3B8");  // Subtle slate 400
    private static final int COLOR_BENCH_STREET = Color.parseColor("#1E293B");  // Swiss charcoal
    private static final int COLOR_BENCH_PARK = Color.parseColor("#2D6A4F");    // Emerald sage
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
    private final Paint paintBenchSelected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchSelectedGap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBenchSelectedCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintWalkLine = new Paint(Paint.ANTI_ALIAS_FLAG);

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
    private final float[] lineBuffer = new float[4096];

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
        paintShoreline.setStrokeWidth(1.6f * density);

        // Parks & Green Spaces
        paintPark.setColor(COLOR_PARK);
        paintPark.setStyle(Paint.Style.FILL);

        paintParkBorder.setColor(COLOR_PARK_BORDER);
        paintParkBorder.setStyle(Paint.Style.STROKE);
        paintParkBorder.setStrokeWidth(0.8f * density);

        // Major Streets (Clean Slate 700)
        paintStreetMajor.setColor(COLOR_STREET_MAJOR);
        paintStreetMajor.setStyle(Paint.Style.STROKE);
        paintStreetMajor.setStrokeWidth(2.0f * density);
        paintStreetMajor.setStrokeCap(Paint.Cap.ROUND);
        paintStreetMajor.setStrokeJoin(Paint.Join.ROUND);

        // Minor Streets (Subtle Hairline Slate 400)
        paintStreetMinor.setColor(COLOR_STREET_MINOR);
        paintStreetMinor.setStyle(Paint.Style.STROKE);
        paintStreetMinor.setStrokeWidth(1.1f * density);
        paintStreetMinor.setStrokeCap(Paint.Cap.ROUND);
        paintStreetMinor.setStrokeJoin(Paint.Join.ROUND);

        // Benches
        paintBenchStreet.setColor(COLOR_BENCH_STREET);
        paintBenchStreet.setStyle(Paint.Style.FILL);

        paintBenchPark.setColor(COLOR_BENCH_PARK);
        paintBenchPark.setStyle(Paint.Style.FILL);

        // Selected Bench Rings
        paintBenchSelected.setColor(COLOR_SWISS_RED);
        paintBenchSelected.setStyle(Paint.Style.STROKE);
        paintBenchSelected.setStrokeWidth(2.4f * density);

        paintBenchSelectedGap.setColor(COLOR_LAND);
        paintBenchSelectedGap.setStyle(Paint.Style.STROKE);
        paintBenchSelectedGap.setStrokeWidth(2.0f * density);

        paintBenchSelectedCore.setColor(COLOR_SWISS_RED);
        paintBenchSelectedCore.setStyle(Paint.Style.FILL);

        // Walk Guidance Line
        paintWalkLine.setColor(COLOR_SWISS_RED);
        paintWalkLine.setStyle(Paint.Style.STROKE);
        paintWalkLine.setStrokeWidth(2.0f * density);
        paintWalkLine.setPathEffect(new DashPathEffect(new float[]{10f * density, 8f * density}, 0));

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

                // Exact focal-point zoom invariance
                float wHalf = getWidth() * 0.5f;
                float hHalf = getHeight() * 0.5f;
                double scaleDiff = (1.0 / oldScale) - (1.0 / newScale);

                centerMercX += (focusX - wHalf) * scaleDiff;
                centerMercY -= (focusY - hHalf) * scaleDiff;

                // Smooth focal pan during pinch with correct cartographic orientation
                float deltaFocusX = focusX - lastFocusX;
                float deltaFocusY = focusY - lastFocusY;
                centerMercX -= deltaFocusX / newScale;
                centerMercY += deltaFocusY / newScale; // Positive Y down moves center north

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
                if (now - lastPointerUpTime < 250 || now - lastScaleEndTime < 250) {
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
                if (now - lastPointerUpTime < 250 || now - lastScaleEndTime < 250) {
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

        if (event.getPointerCount() == 1 && !isScaling && !scaleDetector.isInProgress()) {
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
                if (version != 2) {
                    throw new IllegalStateException("Expected map version 2, got: " + version);
                }

                // 2. String Tables
                String[] parks = readStringTable(in);
                String[] materials = readStringTable(in);
                String[] streets = readStringTable(in);

                // 3. Geometry Layers
                List<GeometryLayer> loadedIsland = readGeometryLayers(in);
                List<GeometryLayer> loadedParks = readGeometryLayers(in);
                List<GeometryLayer> loadedMajor = readGeometryLayers(in);
                List<GeometryLayer> loadedMinor = readGeometryLayers(in);

                // 4. Benches (with street index)
                int benchCount = in.readInt();
                List<Bench> loadedBenches = new ArrayList<>(benchCount);
                for (int i = 0; i < benchCount; i++) {
                    float lat = in.readFloat();
                    float lon = in.readFloat();
                    float mx = in.readFloat();
                    float my = in.readFloat();
                    short pIdx = in.readShort();
                    short sIdx = in.readShort();
                    short mIdx = in.readShort();
                    byte backrest = in.readByte();
                    byte seats = in.readByte();

                    String park = (pIdx >= 0 && pIdx < parks.length) ? parks[pIdx] : "";
                    String street = (sIdx >= 0 && sIdx < streets.length) ? streets[sIdx] : "";
                    String material = (mIdx >= 0 && mIdx < materials.length) ? materials[mIdx] : "";

                    loadedBenches.add(new Bench(lat, lon, mx, my, park, street, material, backrest, seats));
                }

                SpatialBenchIndex newIndex = new SpatialBenchIndex(loadedBenches);

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

        // Viewport bounds in Mercator space
        double viewMinX = screenToMercX(-40);
        double viewMaxX = screenToMercX(w + 40);
        double viewMinY = screenToMercY(h + 40);
        double viewMaxY = screenToMercY(-40);

        synchronized (dataLock) {
            // 2. Island Landmass
            for (int i = 0; i < islandPolys.size(); i++) {
                GeometryLayer poly = islandPolys.get(i);
                if (poly.maxX < viewMinX || poly.minX > viewMaxX || poly.maxY < viewMinY || poly.minY > viewMaxY) {
                    continue;
                }
                pathPoly.reset();
                pathPoly.moveTo(screenX(poly.coords[0]), screenY(poly.coords[1]));
                for (int j = 2; j < poly.coords.length; j += 2) {
                    pathPoly.lineTo(screenX(poly.coords[j]), screenY(poly.coords[j + 1]));
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
                pathPoly.moveTo(screenX(poly.coords[0]), screenY(poly.coords[1]));
                for (int j = 2; j < poly.coords.length; j += 2) {
                    pathPoly.lineTo(screenX(poly.coords[j]), screenY(poly.coords[j + 1]));
                }
                pathPoly.close();
                canvas.drawPath(pathPoly, paintPark);
                if (scale > 180000f) {
                    canvas.drawPath(pathPoly, paintParkBorder);
                }
            }

            // 4. Minor Streets (Batch line rendering)
            if (scale > 75000f) {
                float minorWidth = (scale > 400000f) ? (1.3f * density) : (0.9f * density);
                paintStreetMinor.setStrokeWidth(minorWidth);
                drawLayerLines(canvas, minorStreets, viewMinX, viewMaxX, viewMinY, viewMaxY, paintStreetMinor);
            }

            // 5. Major Streets (Batch line rendering)
            float majorWidth = (scale > 600000f) ? (2.4f * density)
                    : ((scale > 200000f) ? (1.8f * density) : (1.3f * density));
            paintStreetMajor.setStrokeWidth(majorWidth);
            drawLayerLines(canvas, majorStreets, viewMinX, viewMaxX, viewMinY, viewMaxY, paintStreetMajor);

            // 6. Walk Guidance Line
            if (userLocation != null && selectedBench != null) {
                float ux = screenX(lonToMercatorX(userLocation.getLongitude()));
                float uy = screenY(latToMercatorY(userLocation.getLatitude()));
                float bx = screenX(selectedBench.mercX);
                float by = screenY(selectedBench.mercY);
                canvas.drawLine(ux, uy, bx, by, paintWalkLine);
            }

            // 7. Benches
            float benchRadius = (scale > 1800000f) ? (5.0f * density)
                    : ((scale > 600000f) ? (3.6f * density)
                    : ((scale > 220000f) ? (2.5f * density) : (1.8f * density)));

            int step = (scale < 85000f) ? 3 : 1;
            for (int i = 0; i < allBenches.size(); i += step) {
                Bench b = allBenches.get(i);
                if (b.mercX < viewMinX || b.mercX > viewMaxX || b.mercY < viewMinY || b.mercY > viewMaxY) {
                    continue;
                }
                float bx = screenX(b.mercX);
                float by = screenY(b.mercY);
                canvas.drawCircle(bx, by, benchRadius, b.isInPark() ? paintBenchPark : paintBenchStreet);
            }

            // 8. Selected Bench Highlight Ring
            if (selectedBench != null) {
                float bx = screenX(selectedBench.mercX);
                float by = screenY(selectedBench.mercY);

                canvas.drawCircle(bx, by, benchRadius + 7.5f * density, paintBenchSelected);
                canvas.drawCircle(bx, by, benchRadius + 4.0f * density, paintBenchSelectedGap);
                canvas.drawCircle(bx, by, benchRadius + 1.2f * density, paintBenchSelectedCore);
            }
        }

        // 9. User Precision Swiss Pin & Compass Heading Cone
        if (userLocation != null) {
            float ux = screenX(lonToMercatorX(userLocation.getLongitude()));
            float uy = screenY(latToMercatorY(userLocation.getLatitude()));
            drawUserPin(canvas, ux, uy, userLocation.hasAccuracy() ? userLocation.getAccuracy() : 0);
        }
    }

    /**
     * Hardware-accelerated drawLines using pre-allocated float buffer.
     */
    private void drawLayerLines(Canvas canvas, List<GeometryLayer> layers,
                                double minX, double maxX, double minY, double maxY, Paint paint) {
        int bufIdx = 0;
        int maxCap = lineBuffer.length;

        for (int i = 0; i < layers.size(); i++) {
            GeometryLayer layer = layers.get(i);
            if (layer.maxX < minX || layer.minX > maxX || layer.maxY < minY || layer.minY > maxY) {
                continue;
            }

            float[] c = layer.coords;
            int numPts = c.length;
            for (int j = 0; j < numPts - 2; j += 2) {
                if (bufIdx + 4 > maxCap) {
                    canvas.drawLines(lineBuffer, 0, bufIdx, paint);
                    bufIdx = 0;
                }
                lineBuffer[bufIdx++] = screenX(c[j]);
                lineBuffer[bufIdx++] = screenY(c[j + 1]);
                lineBuffer[bufIdx++] = screenX(c[j + 2]);
                lineBuffer[bufIdx++] = screenY(c[j + 3]);
            }
        }

        if (bufIdx > 0) {
            canvas.drawLines(lineBuffer, 0, bufIdx, paint);
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
