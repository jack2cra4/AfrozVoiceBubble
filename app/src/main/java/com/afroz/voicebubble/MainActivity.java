package com.afroz.voicebubble;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.afroz.voicebubble.bubble.FloatingBubbleService;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btn_start_bubble);
        Button btnStop = findViewById(R.id.btn_stop_bubble);
        Button btnAccessibility = findViewById(R.id.btn_open_accessibility);

        btnStart.setOnClickListener(v -> {
            ensureRecordAudioPermission();
            if (!canDrawOverlays()) {
                promptOverlayPermission();
                return;
            }
            startService(new android.content.Intent(this, FloatingBubbleService.class));
            Toast.makeText(this, "बबल चालू", Toast.LENGTH_SHORT).show();
            com.afroz.voicebubble.App.get().getTts()
                    .speak("नमस्ते, मैं हिंदी में बोलती हूँ।");
        });

        btnStop.setOnClickListener(v -> {
            stopService(new android.content.Intent(this, FloatingBubbleService.class));
            Toast.makeText(this, "बबल बंद", Toast.LENGTH_SHORT).show();
        });

        btnAccessibility.setOnClickListener(v -> openAccessibilitySettings());
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void promptOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "कृपया 'अन्य ऐप्स के ऊपर प्रदर्शित करें' अनुमति दें", Toast.LENGTH_LONG).show();
        }
    }

    private void ensureRecordAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            }
        }
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        Toast.makeText(this, "AfrozVoiceBubble सक्षम करें", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "माइक्रोफ़ोन अनुमति मिल गई", Toast.LENGTH_SHORT).show();
        }
    }
}
