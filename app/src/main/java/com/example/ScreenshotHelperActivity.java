package com.example;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenshotHelperActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SCREENSHOT = 3001;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Intialize display measurements
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (projectionManager != null) {
            // Prompt user with native media capture permission dialog
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_CODE_SCREENSHOT);
        } else {
            Toast.makeText(this, "Media Projection is not supported on this device", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SCREENSHOT) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    // Notify/promote FloatingService to FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION before instantiating MediaProjection
                    try {
                        Intent serviceIntent = new Intent(this, FloatingService.class);
                        serviceIntent.setAction("ACTION_START_PROJECTION");
                        startService(serviceIntent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // A brief 100ms pause to ensure Android OS recognizes the foreground type transition has begun
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {}

                    // Instatiate projection safely
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    if (mediaProjection != null) {
                        takeScreenshot();
                    } else {
                        Toast.makeText(this, "Capture permission denied", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Media projection construction failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                Toast.makeText(this, "Screenshot cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void takeScreenshot() {
        // Wait briefly so that our helper transparent activity completely fades or doesn't disrupt layout math
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                // Initialize ImageReader for screen pixels acquisition
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
                
                // Establish mirroring Virtual Display
                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "SAFI_Screenshot",
                        screenWidth,
                        screenHeight,
                        screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                        imageReader.getSurface(),
                        null,
                        null
                );

                // Read screen buffers
                imageReader.setOnImageAvailableListener(reader -> {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            // Unregister listener immediately to ensure we only process a single frame
                            try {
                                imageReader.setOnImageAvailableListener(null, null);
                            } catch (Exception ignored) {}

                            // Extract bytes and generate Bitmap
                            Image.Plane[] planes = image.getPlanes();
                            ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();
                            int rowPadding = rowStride - pixelStride * screenWidth;

                            // Create bitmap with padding correction
                            Bitmap bitmap = Bitmap.createBitmap(
                                    screenWidth + rowPadding / pixelStride,
                                    screenHeight,
                                    Bitmap.Config.ARGB_8888
                            );
                            bitmap.copyPixelsFromBuffer(buffer);

                            // Crop bitmap to actual screen size if row padding corrected it wider
                            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);

                            // Save bitmap
                            saveImageToDisk(croppedBitmap);
                            
                            // Tear down projection structures
                            stopProjection();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(ScreenshotHelperActivity.this, "Capture error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        stopProjection();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }, new Handler(Looper.getMainLooper()));

            } catch (Exception e) {
                Toast.makeText(this, "Failed to capture mirror: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        }, 300);
    }

    private void saveImageToDisk(Bitmap bitmap) {
        try {
            // Establish target directory in standard DCIM system folder
            File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File safiDir = new File(dcimDir, "FloatingSAFI");
            if (!safiDir.exists()) {
                safiDir.mkdirs();
            }

            // Filename based on timestamp
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "SAFI_" + timeStamp + ".png";
            File imageFile = new File(safiDir, fileName);

            // Output stream
            FileOutputStream out = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

            // Broadast gallery refresh
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(imageFile);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);

            Toast.makeText(this, "Screenshot saved: " + fileName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        // Notify FloatingService to demote from media projection
        try {
            Intent serviceIntent = new Intent(this, FloatingService.class);
            serviceIntent.setAction("ACTION_STOP_PROJECTION");
            startService(serviceIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProjection();
    }
}
