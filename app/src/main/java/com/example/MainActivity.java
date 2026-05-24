package com.example;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CODE_OVERLAY_PERMISSION = 1001;

    private TextView statusText;
    private Button btnToggleService;
    private EditText etApiKey;
    private EditText etPin;
    private SeekBar sbOpacity;
    private SeekBar sbSize;
    private TextView tvOpacityLabel;
    private TextView tvSizeLabel;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("FloatingSafiPrefs", MODE_PRIVATE);

        // Bind Views
        statusText = findViewById(R.id.statusText);
        btnToggleService = findViewById(R.id.btnToggleService);
        etApiKey = findViewById(R.id.etApiKey);
        etPin = findViewById(R.id.etPin);
        sbOpacity = findViewById(R.id.sbOpacity);
        sbSize = findViewById(R.id.sbSize);
        tvOpacityLabel = findViewById(R.id.tvOpacityLabel);
        tvSizeLabel = findViewById(R.id.tvSizeLabel);
        Button btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
        Button btnSavePin = findViewById(R.id.btnSavePin);
        Button btnOpenVault = findViewById(R.id.btnOpenVault);

        // Load Saved Configuration
        loadSavedConfig();

        // Service Toggle Listener
        btnToggleService.setOnClickListener(v -> {
            if (isServiceRunning(FloatingService.class)) {
                stopFloatingService();
            } else {
                checkOverlayPermissionAndStart();
            }
        });

        // Config Save Listeners
        btnSaveApiKey.setOnClickListener(v -> {
            String apiKey = etApiKey.getText().toString().trim();
            prefs.edit().putString("groq_api_key", apiKey).apply();
            Toast.makeText(this, "Groq API Key saved successfully!", Toast.LENGTH_SHORT).show();
            notifyServiceConfigChanged();
        });

        btnSavePin.setOnClickListener(v -> {
            String pinStr = etPin.getText().toString().trim();
            if (pinStr.length() == 4) {
                prefs.edit().putString("vault_pin", pinStr).apply();
                Toast.makeText(this, "Vault security PIN set to: " + pinStr, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "PIN must be exactly 4 digits!", Toast.LENGTH_SHORT).show();
            }
        });

        btnOpenVault.setOnClickListener(v -> {
            // Direct launch to test screen
            Intent intent = new Intent(MainActivity.this, VaultActivity.class);
            startActivity(intent);
        });

        // Opacity Slider Listener
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 15) {
                    seekBar.setProgress(15);
                    progress = 15; // Set a minimum opacity of 15% to avoid absolute invisibility
                }
                tvOpacityLabel.setText("Bubble Opacity: " + progress + "%");
                prefs.edit().putInt("bubble_opacity", progress).apply();
                notifyServiceConfigChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Size Slider Listener
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                String sizeLabel = "Medium";
                if (progress == 0) sizeLabel = "Small";
                if (progress == 2) sizeLabel = "Large";
                    
                tvSizeLabel.setText("Bubble Size: " + sizeLabel);
                prefs.edit().putInt("bubble_size", progress).apply();
                notifyServiceConfigChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceUIStatus();
    }

    private void loadSavedConfig() {
        String savedKey = prefs.getString("groq_api_key", "");
        etApiKey.setText(savedKey);

        String savedPin = prefs.getString("vault_pin", "1234");
        etPin.setText(savedPin);

        int savedOpacity = prefs.getInt("bubble_opacity", 80);
        sbOpacity.setProgress(savedOpacity);
        tvOpacityLabel.setText("Bubble Opacity: " + savedOpacity + "%");

        int savedSize = prefs.getInt("bubble_size", 1);
        sbSize.setProgress(savedSize);
        String sizeLabel = "Medium";
        if (savedSize == 0) sizeLabel = "Small";
        if (savedSize == 2) sizeLabel = "Large";
        tvSizeLabel.setText("Bubble Size: " + sizeLabel);

        updateServiceUIStatus();
    }

    private void updateServiceUIStatus() {
        if (isServiceRunning(FloatingService.class)) {
            statusText.setText("Service Status: running");
            statusText.setTextColor(0xFF33FF55); // Green color
            btnToggleService.setText("Stop Floating Service");
            btnToggleService.setBackgroundColor(0xFFFF3366); // Red color
        } else {
            statusText.setText("Service Status: OFF");
            statusText.setTextColor(0xFFFF3366); // Red color
            btnToggleService.setText("Start Floating Service");
            btnToggleService.setBackgroundColor(0xFF03DAC6); // Teal color
        }
    }

    private void checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_CODE_OVERLAY_PERMISSION);
            } else {
                startFloatingService();
            }
        } else {
            startFloatingService();
        }
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        // Give service a split-second to launch, then refresh
        statusText.postDelayed(this::updateServiceUIStatus, 300);
    }

    private void stopFloatingService() {
        Intent intent = new Intent(this, FloatingService.class);
        stopService(intent);
        statusText.postDelayed(this::updateServiceUIStatus, 300);
    }

    private void notifyServiceConfigChanged() {
        if (isServiceRunning(FloatingService.class)) {
            Intent intent = new Intent(this, FloatingService.class);
            intent.setAction("ACTION_CONFIG_CHANGED");
            startService(intent);
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
            if (services != null) {
                for (ActivityManager.RunningServiceInfo service : services) {
                    if (service != null && service.service != null && serviceClass.getName().equals(service.service.getClassName())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingService();
                } else {
                    Toast.makeText(this, "Floating Overlay Permission is required for Floating SAFI to draw bubbles over apps!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
