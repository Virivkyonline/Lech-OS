/*
 * Copyright (C) 2026 The LECH OS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lech.os.fotoaparat;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import lech.os.ui.Ui;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The camera of LECH OS: photos and videos with the Camera2 API, flash, zoom (also the wide lens below 1×), grid,
 * front / back camera. Photos and videos go to DCIM/Camera without location. Answers IMAGE_CAPTURE of other apps.
 */
public class CameraActivity extends Activity {
    private static final int FLASH_OFF = 0;
    private static final int FLASH_AUTO = 1;
    private static final int FLASH_ON = 2;
    private static final int REQ_PERMISSIONS = 1;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler cam;
    private CameraManager manager;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CaptureRequest.Builder preview;
    private ImageReader jpeg;
    private Size previewSize;
    private Size videoSize;
    private int sensorOrientation;
    private int deviceOrientation;
    private Range<Float> zoomRange;
    private float zoom = 1f;
    private boolean front;
    private boolean videoMode;
    private int flash = FLASH_OFF;
    private boolean grid;
    private boolean capturing;
    private boolean waitingPrecapture;
    private long precaptureStart;
    private MediaRecorder recorder;
    private ParcelFileDescriptor videoFd;
    private Uri videoUri;
    private boolean recording;
    private long recordStart;
    private boolean captureIntent;
    private Uri captureOutput;

    private FrameLayout previewFrame;
    private TextureView texture;
    private View gridView;
    private View flashOverlay;
    private ImageView flashButton;
    private ImageView gridButton;
    private View topBar;
    private View bottomBar;
    private LinearLayout zoomRow;
    private TextView photoMode;
    private TextView videoModeText;
    private FrameLayout shutter;
    private View shutterInner;
    private ImageView thumb;
    private TextView recTime;
    private LinearLayout permissionView;
    private OrientationEventListener orientation;

    private final Runnable recTick = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            long s = (SystemClock.elapsedRealtime() - recordStart) / 1000;
            recTime.setText(String.format(Locale.ROOT, "● %02d:%02d", s / 60, s % 60));
            main.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = getSystemService(CameraManager.class);
        String action = getIntent().getAction();
        captureIntent = MediaStore.ACTION_IMAGE_CAPTURE.equals(action);
        captureOutput = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri.class);
        boolean secure = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action);
        if (secure) setShowWhenLocked(true);
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(action) || MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(action)) {
            videoMode = true;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        previewFrame = new FrameLayout(this);
        texture = new TextureView(this);
        previewFrame.addView(texture, Ui.match());
        gridView = new GridView(this);
        gridView.setVisibility(View.GONE);
        previewFrame.addView(gridView, Ui.match());
        flashOverlay = new View(this);
        flashOverlay.setBackgroundColor(0xFFFFFFFF);
        flashOverlay.setAlpha(0f);
        previewFrame.addView(flashOverlay, Ui.match());
        root.addView(previewFrame, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout top = Ui.row(this);
        top.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        FrameLayout fb = Ui.roundButton(this, R.drawable.ic_flash_off, false, 46, getString(R.string.flash),
                v -> cycleFlash());
        flashButton = (ImageView) fb.getChildAt(0);
        top.addView(fb);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.lech_logo);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams ll = Ui.lp(0, Ui.dp(this, 30), 1);
        top.addView(logo, ll);
        FrameLayout gb = Ui.roundButton(this, R.drawable.ic_grid, false, 46, getString(R.string.grid), v -> {
            grid = !grid;
            gridView.setVisibility(grid ? View.VISIBLE : View.GONE);
            gridButton.setImageTintList(ColorStateList.valueOf(grid ? Ui.GREEN : Ui.TEXT));
        });
        gridButton = (ImageView) gb.getChildAt(0);
        top.addView(gb);
        topBar = top;
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        recTime = Ui.text(this, "", 18, 0xFFFF5252, true);
        recTime.setBackground(Ui.shape(this, 0x99000000, 0, 0, 14));
        recTime.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        recTime.setVisibility(View.GONE);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rl.topMargin = Ui.dp(this, 70);
        root.addView(recTime, rl);

        LinearLayout bottom = Ui.column(this);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0xEE000000, 0x88000000, 0x00000000}));
        bottom.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 16));
        zoomRow = Ui.row(this);
        zoomRow.setGravity(Gravity.CENTER);
        bottom.addView(zoomRow, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 12));
        LinearLayout modes = Ui.row(this);
        modes.setGravity(Gravity.CENTER);
        photoMode = Ui.text(this, getString(R.string.mode_photo), 15, Ui.TEXT, true);
        videoModeText = Ui.text(this, getString(R.string.mode_video), 15, Ui.TEXT, true);
        for (TextView t : new TextView[] {photoMode, videoModeText}) {
            t.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 6));
            t.setLetterSpacing(0.1f);
            modes.addView(t, Ui.margins(Ui.lp(-2, -2), this, 6, 0, 6, 0));
        }
        photoMode.setOnClickListener(v -> setMode(false));
        videoModeText.setOnClickListener(v -> setMode(true));
        if (captureIntent) modes.setVisibility(View.GONE);
        bottom.addView(modes, Ui.margins(Ui.lp(-1, -2), this, 0, 0, 0, 14));
        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(Ui.dp(this, 30), 0, Ui.dp(this, 30), 0);
        thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(Ui.shape(this, 0x33FFFFFF, 0xAAFFFFFF, 1.5f, 14));
        thumb.setContentDescription(getString(R.string.last_photo));
        thumb.setClipToOutline(true);
        thumb.setOnClickListener(v -> openLast());
        if (captureIntent || secure) thumb.setVisibility(View.INVISIBLE);
        controls.addView(thumb, Ui.lp(Ui.dp(this, 58), Ui.dp(this, 58)));
        controls.addView(new View(this), Ui.lp(0, 1, 1));
        shutter = new FrameLayout(this);
        GradientDrawable ring = Ui.circle(0x00000000, 0xFFFFFFFF, Ui.dp(this, 4));
        shutter.setBackground(ring);
        shutterInner = new View(this);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.dp(this, 66), Ui.dp(this, 66), Gravity.CENTER);
        shutter.addView(shutterInner, il);
        shutter.setElevation(Ui.dp(this, 6));
        shutter.setOnClickListener(v -> shutter());
        controls.addView(shutter, Ui.lp(Ui.dp(this, 84), Ui.dp(this, 84)));
        controls.addView(new View(this), Ui.lp(0, 1, 1));
        controls.addView(Ui.roundButton(this, R.drawable.ic_flip_camera, false, 58, getString(R.string.switch_camera),
                v -> switchCamera()));
        bottom.addView(controls, Ui.lp(-1, -2));
        bottomBar = bottom;
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        permissionView = Ui.column(this);
        permissionView.setGravity(Gravity.CENTER);
        permissionView.setBackgroundColor(0xFF000000);
        permissionView.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        TextView pt = Ui.text(this, getString(R.string.permission_needed), 18, Ui.TEXT, false);
        pt.setGravity(Gravity.CENTER);
        permissionView.addView(pt);
        permissionView.addView(Ui.pillButton(this, 0, getString(R.string.permission_grant), v -> askPermissions()),
                Ui.margins(Ui.lp(-2, -2), this, 0, 24, 0, 0));
        permissionView.setVisibility(View.GONE);
        root.addView(permissionView, Ui.match());

        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            topBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 8) + i.top, Ui.dp(this, 14), Ui.dp(this, 8));
            bottomBar.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 16) + i.bottom);
            FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) previewFrame.getLayoutParams();
            pl.topMargin = i.top + Ui.dp(this, 62);
            previewFrame.setLayoutParams(pl);
            FrameLayout.LayoutParams r2 = (FrameLayout.LayoutParams) recTime.getLayoutParams();
            r2.topMargin = i.top + Ui.dp(this, 70);
            recTime.setLayoutParams(r2);
            return WindowInsets.CONSUMED;
        });
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(root);

        ScaleGestureDetector pinch = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        setZoom(zoom * d.getScaleFactor());
                        return true;
                    }
                });
        texture.setOnTouchListener((v, e) -> {
            pinch.onTouchEvent(e);
            return true;
        });
        orientation = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int o) {
                if (o != ORIENTATION_UNKNOWN) deviceOrientation = (o + 45) / 90 * 90 % 360;
            }
        };
        updateMode();
        updateFlash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        orientation.enable();
        loadThumb();
        if (!hasPermissions()) {
            permissionView.setVisibility(View.VISIBLE);
            askPermissions();
            return;
        }
        permissionView.setVisibility(View.GONE);
        thread = new HandlerThread("camera");
        thread.start();
        cam = new Handler(thread.getLooper());
        if (texture.isAvailable()) {
            open();
        } else {
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
                    open();
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
                    return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture s) { }
            });
        }
    }

    @Override
    protected void onPause() {
        if (recording) stopRecording();
        close();
        if (thread != null) {
            thread.quitSafely();
            thread = null;
            cam = null;
        }
        orientation.disable();
        super.onPause();
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermissions() {
        requestPermissions(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                REQ_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (hasPermissions()) recreate();
    }

    // ---- Camera ----

    private String cameraId() throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing == null) continue;
            boolean isFront = facing == CameraCharacteristics.LENS_FACING_FRONT;
            if (isFront == front) return id;
            if (fallback == null) fallback = id;
        }
        return fallback;
    }

    private void open() {
        if (cam == null) return;
        try {
            String id = cameraId();
            if (id == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
            CameraCharacteristics ch = manager.getCameraCharacteristics(id);
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so == null ? 90 : so;
            zoomRange = ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            zoom = 1f;
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size jpegSize = largest(map.getOutputSizes(ImageFormat.JPEG), 4f / 3f);
            videoSize = pick(map.getOutputSizes(MediaRecorder.class), 16f / 9f, 1920);
            float ratio = videoMode ? 16f / 9f : (float) jpegSize.getWidth() / jpegSize.getHeight();
            previewSize = pick(map.getOutputSizes(SurfaceTexture.class), ratio, 1920);
            if (jpeg != null) jpeg.close();
            jpeg = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
            jpeg.setOnImageAvailableListener(this::onJpeg, cam);
            main.post(this::layoutPreview);
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice d) {
                    device = d;
                    startPreview(null);
                }

                @Override
                public void onDisconnected(CameraDevice d) {
                    d.close();
                    device = null;
                }

                @Override
                public void onError(CameraDevice d, int error) {
                    d.close();
                    device = null;
                    main.post(() -> Toast.makeText(CameraActivity.this, R.string.camera_error,
                            Toast.LENGTH_SHORT).show());
                }
            }, cam);
            main.post(this::buildZoomChips);
        } catch (CameraAccessException | SecurityException | NullPointerException e) {
            Toast.makeText(this, R.string.camera_error, Toast.LENGTH_SHORT).show();
        }
    }

    private static Size largest(Size[] sizes, float ratio) {
        Size best = null;
        for (Size s : sizes) {
            if (Math.abs((float) s.getWidth() / s.getHeight() - ratio) > 0.02f) continue;
            if (best == null || (long) s.getWidth() * s.getHeight() > (long) best.getWidth() * best.getHeight()) {
                best = s;
            }
        }
        if (best == null) {
            List<Size> all = new ArrayList<>(Arrays.asList(sizes));
            Collections.sort(all, (a, b) -> Long.compare((long) b.getWidth() * b.getHeight(),
                    (long) a.getWidth() * a.getHeight()));
            best = all.get(0);
        }
        return best;
    }

    private static Size pick(Size[] sizes, float ratio, int maxWidth) {
        Size best = null;
        for (Size s : sizes) {
            if (Math.abs((float) s.getWidth() / s.getHeight() - ratio) > 0.02f || s.getWidth() > maxWidth) continue;
            if (best == null || s.getWidth() > best.getWidth()) best = s;
        }
        return best != null ? best : sizes[0];
    }

    private void layoutPreview() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = Math.round(w * (float) previewSize.getWidth() / previewSize.getHeight());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) previewFrame.getLayoutParams();
        lp.width = w;
        lp.height = h;
        previewFrame.setLayoutParams(lp);
    }

    private void startPreview(Surface recordSurface) {
        if (device == null || !texture.isAvailable()) return;
        try {
            SurfaceTexture st = texture.getSurfaceTexture();
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface view = new Surface(st);
            List<Surface> targets = new ArrayList<>();
            targets.add(view);
            if (recordSurface != null) {
                targets.add(recordSurface);
            } else if (!videoMode) {
                targets.add(jpeg.getSurface());
            }
            if (session != null) {
                session.close();
                session = null;
            }
            preview = device.createCaptureRequest(recordSurface != null ? CameraDevice.TEMPLATE_RECORD
                    : CameraDevice.TEMPLATE_PREVIEW);
            preview.addTarget(view);
            if (recordSurface != null) preview.addTarget(recordSurface);
            device.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession s) {
                    session = s;
                    repeat();
                    if (recordSurface != null) {
                        try {
                            recorder.start();
                            recording = true;
                            recordStart = SystemClock.elapsedRealtime();
                            main.post(() -> {
                                recTime.setVisibility(View.VISIBLE);
                                main.post(recTick);
                                updateMode();
                            });
                        } catch (Exception e) {
                            main.post(() -> {
                                Toast.makeText(CameraActivity.this, R.string.video_failed, Toast.LENGTH_SHORT).show();
                                releaseRecorder(false);
                            });
                        }
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession s) {
                    main.post(() -> Toast.makeText(CameraActivity.this, R.string.camera_error,
                            Toast.LENGTH_SHORT).show());
                }
            }, cam);
        } catch (CameraAccessException | IllegalStateException e) {
            main.post(() -> Toast.makeText(this, R.string.camera_error, Toast.LENGTH_SHORT).show());
        }
    }

    private void applyCommon(CaptureRequest.Builder b, boolean still) {
        b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        b.set(CaptureRequest.CONTROL_AF_MODE, videoMode ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        if (zoomRange != null) b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
        if (videoMode) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            b.set(CaptureRequest.FLASH_MODE, flash == FLASH_ON ? CaptureRequest.FLASH_MODE_TORCH
                    : CaptureRequest.FLASH_MODE_OFF);
        } else if (flash == FLASH_AUTO) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else if (flash == FLASH_ON) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
        } else {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            b.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private void repeat() {
        if (session == null || preview == null) return;
        try {
            applyCommon(preview, false);
            session.setRepeatingRequest(preview.build(), watcher, cam);
        } catch (CameraAccessException | IllegalStateException ignored) {
            // Session closed meanwhile.
        }
    }

    /** Watches the preview for the end of the flash metering (precapture) before a photo with flash. */
    private final CameraCaptureSession.CaptureCallback watcher = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
            if (!waitingPrecapture) return;
            Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
            boolean done = ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED
                    || ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                    || ae == CaptureResult.CONTROL_AE_STATE_LOCKED;
            if ((done && SystemClock.elapsedRealtime() - precaptureStart > 150)
                    || SystemClock.elapsedRealtime() - precaptureStart > 1500) {
                waitingPrecapture = false;
                captureStill();
            }
        }
    };

    private void close() {
        try {
            if (session != null) session.close();
        } catch (Exception ignored) {
            // Already closed.
        }
        session = null;
        if (device != null) device.close();
        device = null;
        if (jpeg != null) {
            jpeg.close();
            jpeg = null;
        }
        capturing = false;
        waitingPrecapture = false;
    }

    private int captureOrientation() {
        int d = front ? -deviceOrientation : deviceOrientation;
        return (sensorOrientation + d + 360) % 360;
    }

    // ---- Photo ----

    private void shutter() {
        if (device == null || session == null) return;
        if (videoMode) {
            if (recording) stopRecording(); else startRecording();
            return;
        }
        if (capturing) return;
        capturing = true;
        if (flash != FLASH_OFF && cam != null) {
            cam.post(() -> {
                try {
                    applyCommon(preview, false);
                    preview.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                    session.capture(preview.build(), watcher, cam);
                    preview.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                    precaptureStart = SystemClock.elapsedRealtime();
                    waitingPrecapture = true;
                } catch (CameraAccessException | IllegalStateException e) {
                    captureStill();
                }
            });
        } else if (cam != null) {
            cam.post(this::captureStill);
        }
    }

    private void captureStill() {
        if (device == null || session == null) {
            capturing = false;
            return;
        }
        try {
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(jpeg.getSurface());
            applyCommon(b, true);
            b.set(CaptureRequest.JPEG_ORIENTATION, captureOrientation());
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            session.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession s, CaptureRequest r, long ts, long frame) {
                    main.post(() -> {
                        flashOverlay.setAlpha(0.8f);
                        flashOverlay.animate().alpha(0f).setDuration(250).start();
                    });
                }
            }, cam);
        } catch (CameraAccessException | IllegalStateException e) {
            capturing = false;
        }
    }

    private void onJpeg(ImageReader r) {
        byte[] bytes;
        try (Image img = r.acquireLatestImage()) {
            if (img == null) return;
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            bytes = new byte[buf.remaining()];
            buf.get(bytes);
        }
        capturing = false;
        repeat();
        if (captureIntent) {
            deliverCapture(bytes);
            return;
        }
        Uri uri = saveJpeg(bytes);
        main.post(() -> {
            if (uri == null) {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
            } else {
                showThumb(bytes);
            }
        });
    }

    private Uri saveJpeg(byte[] bytes) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + stamp() + ".jpg");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera");
        v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) return null;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            os.write(bytes);
        } catch (Exception e) {
            getContentResolver().delete(uri, null, null);
            return null;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        getContentResolver().update(uri, done, null, null);
        return uri;
    }

    /** IMAGE_CAPTURE of another app: the photo goes to its EXTRA_OUTPUT, or a small picture comes back. */
    private void deliverCapture(byte[] bytes) {
        Intent result = new Intent();
        boolean ok = true;
        if (captureOutput != null) {
            try (OutputStream os = getContentResolver().openOutputStream(captureOutput)) {
                os.write(bytes);
            } catch (Exception e) {
                ok = false;
            }
        } else {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (b != null) {
                Matrix m = new Matrix();
                m.postRotate(captureOrientation());
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            result.putExtra("data", b);
        }
        final boolean fOk = ok;
        main.post(() -> {
            setResult(fOk ? RESULT_OK : RESULT_CANCELED, result);
            finish();
        });
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
    }

    // ---- Video ----

    private void startRecording() {
        if (cam == null) return;
        cam.post(() -> {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Video.Media.DISPLAY_NAME, "VID_" + stamp() + ".mp4");
                v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera");
                v.put(MediaStore.Video.Media.IS_PENDING, 1);
                videoUri = getContentResolver().insert(MediaStore.Video.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                videoFd = getContentResolver().openFileDescriptor(videoUri, "w");
                recorder = new MediaRecorder(this);
                boolean audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
                if (audio) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setOutputFile(videoFd.getFileDescriptor());
                recorder.setVideoEncodingBitRate(16_000_000);
                recorder.setVideoFrameRate(30);
                recorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                if (audio) {
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    recorder.setAudioSamplingRate(48000);
                    recorder.setAudioEncodingBitRate(192_000);
                }
                recorder.setOrientationHint(captureOrientation());
                recorder.prepare();
                startPreview(recorder.getSurface());
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, R.string.video_failed, Toast.LENGTH_SHORT).show());
                releaseRecorder(false);
            }
        });
    }

    private void stopRecording() {
        recording = false;
        recTime.setVisibility(View.GONE);
        main.removeCallbacks(recTick);
        boolean ok = true;
        try {
            if (session != null) session.stopRepeating();
        } catch (Exception ignored) {
            // Session gone.
        }
        try {
            recorder.stop();
        } catch (Exception e) {
            ok = false;
        }
        releaseRecorder(ok);
        updateMode();
        if (cam != null) cam.post(() -> startPreview(null));
    }

    private void releaseRecorder(boolean keep) {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        try {
            if (videoFd != null) videoFd.close();
        } catch (Exception ignored) {
            // Closed.
        }
        videoFd = null;
        if (videoUri != null) {
            if (keep) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(videoUri, done, null, null);
                final Uri u = videoUri;
                main.post(() -> {
                    loadThumb();
                    if (MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
                        setResult(RESULT_OK, new Intent().setData(u));
                        finish();
                    }
                });
            } else {
                getContentResolver().delete(videoUri, null, null);
            }
        }
        videoUri = null;
    }

    // ---- Controls ----

    private void setMode(boolean video) {
        if (recording || video == videoMode) return;
        videoMode = video;
        updateMode();
        close();
        open();
    }

    private void updateMode() {
        photoMode.setTextColor(videoMode ? Ui.TEXT_2 : Ui.ON_GREEN);
        photoMode.setBackground(videoMode ? null : Ui.greenGlow(this, 16));
        videoModeText.setTextColor(videoMode ? Ui.ON_GREEN : Ui.TEXT_2);
        videoModeText.setBackground(videoMode ? Ui.greenGlow(this, 16) : null);
        GradientDrawable inner = new GradientDrawable();
        if (videoMode) {
            inner.setColor(0xFFFF3B30);
            inner.setCornerRadius(recording ? Ui.dp(this, 8) : Ui.dp(this, 33));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) shutterInner.getLayoutParams();
            lp.width = lp.height = Ui.dp(this, recording ? 34 : 66);
            shutterInner.setLayoutParams(lp);
        } else {
            inner.setShape(GradientDrawable.OVAL);
            inner.setColors(new int[] {0xFFFFFFFF, 0xFFE6FFD6});
            inner.setStroke(Ui.dp(this, 2), Ui.GREEN);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) shutterInner.getLayoutParams();
            lp.width = lp.height = Ui.dp(this, 66);
            shutterInner.setLayoutParams(lp);
        }
        shutterInner.setBackground(inner);
        shutter.setContentDescription(getString(videoMode ? (recording ? R.string.stop : R.string.record)
                : R.string.shutter));
    }

    private void cycleFlash() {
        flash = (flash + 1) % 3;
        if (videoMode && flash == FLASH_AUTO) flash = FLASH_ON;
        updateFlash();
        repeat();
    }

    private void updateFlash() {
        flashButton.setImageResource(flash == FLASH_ON ? R.drawable.ic_flash_on : flash == FLASH_AUTO
                ? R.drawable.ic_flash_auto : R.drawable.ic_flash_off);
        flashButton.setImageTintList(ColorStateList.valueOf(flash == FLASH_OFF ? Ui.TEXT : Ui.GREEN));
    }

    private void switchCamera() {
        if (recording) return;
        front = !front;
        close();
        open();
    }

    private void setZoom(float z) {
        if (zoomRange == null) return;
        zoom = Math.max(zoomRange.getLower(), Math.min(zoomRange.getUpper(), z));
        if (cam != null) cam.post(this::repeat);
        updateZoomChips();
    }

    private void buildZoomChips() {
        zoomRow.removeAllViews();
        if (zoomRange == null) return;
        List<Float> stops = new ArrayList<>();
        if (zoomRange.getLower() < 0.95f) stops.add(Math.max(zoomRange.getLower(), 0.5f));
        stops.add(1f);
        if (zoomRange.getUpper() >= 2f) stops.add(2f);
        if (zoomRange.getUpper() >= 5f) stops.add(5f);
        for (float s : stops) {
            TextView t = Ui.text(this, "", 14, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setTag(s);
            t.setOnClickListener(v -> setZoom((Float) v.getTag()));
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46));
            lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            zoomRow.addView(t, lp);
        }
        updateZoomChips();
    }

    private void updateZoomChips() {
        float nearest = -1;
        for (int k = 0; k < zoomRow.getChildCount(); k++) {
            float s = (Float) zoomRow.getChildAt(k).getTag();
            if (zoom >= s - 0.05f) nearest = s;
        }
        for (int k = 0; k < zoomRow.getChildCount(); k++) {
            TextView t = (TextView) zoomRow.getChildAt(k);
            float s = (Float) t.getTag();
            boolean on = s == nearest;
            float shown = on ? zoom : s;
            String text = (shown == Math.floor(shown) ? String.valueOf((int) shown)
                    : String.format(Locale.getDefault(), "%.1f", shown)) + "×";
            t.setText(text);
            t.setTextColor(on ? Ui.ON_GREEN : Ui.TEXT);
            t.setBackground(on ? Ui.circle(Ui.GREEN, 0xFFC8FF8A, Ui.dp(this, 1))
                    : Ui.circle(0x88000000, 0x66FFFFFF, Ui.dp(this, 1)));
        }
    }

    // ---- Last photo ----

    private Uri lastUri;

    private void loadThumb() {
        new Thread(() -> {
            Uri found = null;
            long foundDate = -1;
            for (Uri base : new Uri[] {MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)}) {
                try (Cursor c = getContentResolver().query(base, new String[] {MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DATE_ADDED}, MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[] {Environment.DIRECTORY_DCIM + "/Camera%"},
                        MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
                    if (c != null && c.moveToFirst() && c.getLong(1) > foundDate) {
                        foundDate = c.getLong(1);
                        found = ContentUris.withAppendedId(base, c.getLong(0));
                    }
                } catch (Exception ignored) {
                    // Only own files are visible without media permissions.
                }
            }
            Bitmap b = null;
            if (found != null) {
                try {
                    b = getContentResolver().loadThumbnail(found, new Size(200, 200), null);
                } catch (Exception ignored) {
                    // No thumbnail.
                }
            }
            final Uri u = found;
            final Bitmap fb = b;
            main.post(() -> {
                lastUri = u;
                if (fb != null) thumb.setImageBitmap(Ui.roundCorners(fb, 24));
            });
        }).start();
    }

    private void showThumb(byte[] jpegBytes) {
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 16;
            Bitmap b = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, o);
            if (b == null) return;
            Matrix m = new Matrix();
            m.postRotate(captureOrientation());
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            main.post(() -> {
                thumb.setImageBitmap(Ui.roundCorners(r, 24));
                loadThumb();
            });
        }).start();
    }

    private void openLast() {
        if (lastUri == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW);
        String type = lastUri.toString().contains("/video/") ? "video/*" : "image/*";
        i.setDataAndType(lastUri, type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
            // No gallery.
        }
    }

    /** Lines of thirds over the preview. */
    private static final class GridView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GridView(Context c) {
            super(c);
            paint.setColor(0x88FFFFFF);
            paint.setStrokeWidth(Ui.dp(c, 1));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            for (int k = 1; k < 3; k++) {
                c.drawLine(w * k / 3f, 0, w * k / 3f, h, paint);
                c.drawLine(0, h * k / 3f, w, h * k / 3f, paint);
            }
        }
    }
}
