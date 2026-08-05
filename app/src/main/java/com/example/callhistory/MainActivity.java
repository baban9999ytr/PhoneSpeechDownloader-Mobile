package com.example.callhistory;

import io.github.jan.supabase.SupabaseClient;
import com.example.callhistory.SupabaseManager;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int RECORDING_SCAN_LIMIT = 200;
    private static final String ALERTS_CHANNEL_ID = "AppAlertsChannel";

    private TextInputEditText etServerUrl;
    private TextInputEditText etPhoneNumber;
    private TextInputEditText etSyncLimit;
    private RadioGroup rgWebhookMode;
    private TextView tvStatus;
    private SharedPreferences sharedPreferences;
    private DatabaseHelper dbHelper;

    SupabaseClient supabase = SupabaseManager.getClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        createAppAlertsChannel();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etServerUrl = findViewById(R.id.etServerUrl);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etSyncLimit = findViewById(R.id.etSyncLimit);
        rgWebhookMode = findViewById(R.id.rgWebhookMode);
        tvStatus = findViewById(R.id.tvStatus);

        dbHelper = new DatabaseHelper(this);
        sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE);

        String savedNumber = sharedPreferences.getString("phone_number", null);
        String savedServer = sharedPreferences.getString("server_url", "http://10.0.2.2:5678/");
        String savedMode = sharedPreferences.getString("webhook_mode", "production");
        String savedLimit = sharedPreferences.getString("sync_limit", "50");
        etServerUrl.setText(savedServer);
        etSyncLimit.setText(savedLimit);
        rgWebhookMode.check("test".equals(savedMode) ? R.id.rbTest : R.id.rbProduction);

        if (savedNumber != null) {
            etPhoneNumber.setText(savedNumber);
            updateUIAsLoggedIn(savedNumber, savedServer, savedMode, savedLimit);

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                startBackgroundSyncService();
            }
        }

        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> saveSettingsAndRequestPermissions());

        Button btnViewLogs = findViewById(R.id.btnViewLogs);
        btnViewLogs.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, CallListActivity.class)));

        Button btnSyncHistorical = findViewById(R.id.btnSyncHistorical);
        btnSyncHistorical.setOnClickListener(v -> {
            saveSettingsOnly();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                int syncLimit = parseSyncLimit(getText(etSyncLimit));
                String label = syncLimit == Integer.MAX_VALUE ? "all calls" : syncLimit + " calls";

                showToastAndNotification("Starting historical sync: " + label);

                new Thread(() -> syncHistoricalCalls(syncLimit)).start();
            } else {
                showToastAndNotification("Permission denied for Call Logs.");
            }
        });
    }


    private void createAppAlertsChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ALERTS_CHANNEL_ID,
                    "App Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showToastAndNotification(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Call History Alert")
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }


    private void saveSettingsAndRequestPermissions() {
        if (saveSettingsOnly()) {
            checkAndRequestPermissions();
        }
    }

    private boolean saveSettingsOnly() {
        String phoneNumber = getText(etPhoneNumber);
        String serverUrl = getText(etServerUrl);
        String syncLimit = getText(etSyncLimit);
        String webhookMode = rgWebhookMode.getCheckedRadioButtonId() == R.id.rbTest ? "test" : "production";

        if (!phoneNumber.matches("^(\\+90|0)?5\\d{9}$")) {
            showToastAndNotification("Please enter a valid phone number");
            return false;
        }
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            showToastAndNotification("Server URL must start with http:// or https://");
            return false;
        }
        if (parseSyncLimit(syncLimit) <= 0) {
            showToastAndNotification("Sync count must be a positive number or all");
            return false;
        }

        sharedPreferences.edit()
                .putString("phone_number", phoneNumber)
                .putString("server_url", serverUrl)
                .putString("webhook_mode", webhookMode)
                .putString("sync_limit", syncLimit.isEmpty() ? "50" : syncLimit)
                .apply();
        return true;
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private int parseSyncLimit(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 50;
        }
        if ("all".equalsIgnoreCase(value.trim())) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        permissions.add(Manifest.permission.READ_CALL_LOG);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(perm);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            updateUIFromPrefs();
            startBackgroundSyncService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                updateUIFromPrefs();
                startBackgroundSyncService();
                showToastAndNotification("Permissions granted. Tracking active.");
            } else {
                showToastAndNotification("Permissions denied. App cannot track metrics properly.");
            }
        }
    }

    private void updateUIFromPrefs() {
        updateUIAsLoggedIn(
                sharedPreferences.getString("phone_number", ""),
                sharedPreferences.getString("server_url", "http://10.0.2.2:5678/"),
                sharedPreferences.getString("webhook_mode", "production"),
                sharedPreferences.getString("sync_limit", "50")
        );
    }

    private void updateUIAsLoggedIn(String phoneNumber, String serverUrl, String webhookMode, String syncLimit) {
        Button btn = findViewById(R.id.btnLogin);
        btn.setEnabled(true);
        btn.setText("Update Settings");

        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Tracking Active\nNumber: " + phoneNumber + "\nServer: " + serverUrl + "\nWebhook: " + webhookMode + "\nHistorical sync: " + syncLimit);
    }

    private String uploadAudioToSupabase(String uriString) {
        if (uriString == null || uriString.isEmpty()) return "";

        try {
            Uri audioUri = Uri.parse(uriString);
            InputStream inputStream = getContentResolver().openInputStream(audioUri);
            if (inputStream == null) return "";

            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] fileBytes = byteBuffer.toByteArray();
            inputStream.close();

            String fileName = "call_" + System.currentTimeMillis() + ".aac";
            SupabaseManager.uploadFile("recordings", fileName, fileBytes);

            return "https://waultfsxvpomjcdwuoem.supabase.co/storage/v1/object/public/recordings/" + fileName;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private void startBackgroundSyncService() {
        try {
            Intent serviceIntent = new Intent(this, CallSyncService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                startService(serviceIntent);
            }
            // Optional: Uncomment the line below if you want a notification every time the app attempts to start the background service
            // showToastAndNotification("Service Start Signalled");
        } catch (Exception e) {
            e.printStackTrace();
            showToastAndNotification("Failed to start service: " + e.getMessage());
        }
    }

    private void syncHistoricalCalls(int syncLimit) {
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC")) {
            if (cursor == null || !cursor.moveToFirst()) {
                showToastAndNotification("No call logs found.");
                return;
            }

            int uploadedCount = 0;
            int attemptedCount = 0;
            do {
                attemptedCount++;
                int numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
                int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);
                int nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);

                String number = numberIdx >= 0 ? cursor.getString(numberIdx) : "";
                if (number == null || number.trim().isEmpty()) {
                    number = "Unknown";
                }
                int callTypeInt = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                long dateStart = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0;
                long durationSec = durationIdx >= 0 ? cursor.getLong(durationIdx) : 0;
                String contactName = nameIdx >= 0 ? cursor.getString(nameIdx) : "Unknown";
                if (contactName == null) contactName = "Unknown";

                long dateEnd = dateStart + (durationSec * 1000);
                String callType = "UNKNOWN";
                int isIncoming = 0;
                int isOutgoing = 0;
                int isMissed = 0;
                int isRejected = 0;
                int isAnswered = 0;

                switch (callTypeInt) {
                    case CallLog.Calls.INCOMING_TYPE:
                        callType = "INCOMING";
                        isIncoming = 1;
                        isAnswered = durationSec > 0 ? 1 : 0;
                        break;
                    case CallLog.Calls.OUTGOING_TYPE:
                        callType = "OUTGOING";
                        isOutgoing = 1;
                        isAnswered = durationSec > 0 ? 1 : 0;
                        break;
                    case CallLog.Calls.MISSED_TYPE:
                        callType = "MISSED";
                        isIncoming = 1;
                        isMissed = 1;
                        break;
                    case CallLog.Calls.REJECTED_TYPE:
                        callType = "REJECTED";
                        isIncoming = 1;
                        isRejected = 1;
                        break;
                    default:
                        break;
                }

                String recordingUri = findHistoricalCallRecording(dateEnd);
                String finalAudioUrl = "";

                if (recordingUri != null && !recordingUri.isEmpty()) {
                    finalAudioUrl = uploadAudioToSupabase(recordingUri);
                }

                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                String carrierName = tm != null ? tm.getNetworkOperatorName() : "";

                boolean uploaded = dbHelper.insertCallRecordAndSyncBlocking(
                        Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, number, contactName,
                        callType, isIncoming, isOutgoing, isMissed, isRejected, isAnswered,
                        dateStart, dateStart, dateEnd, durationSec, 0, durationSec,
                        "40.1885,29.0610", 1, carrierName, "NORMAL_CLEARING", finalAudioUrl
                );

                if (uploaded) {
                    uploadedCount++;
                }
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } while (attemptedCount < syncLimit && cursor.moveToNext());

            int finalUploaded = uploadedCount;
            int finalAttempted = attemptedCount;
            showToastAndNotification("Historical sync uploaded " + finalUploaded + " of " + finalAttempted + " calls.");
        } catch (Exception e) {
            showToastAndNotification("Historical sync failed: " + e.getMessage());
        }
    }

    private String findHistoricalCallRecording(long callEndTimeMs) {
        ContentResolver resolver = getContentResolver();
        Uri audioUri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
        };
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        List<UriData> secondaryCandidates = new ArrayList<>();

        try (Cursor cursor = resolver.query(audioUri, projection, null, null, sortOrder)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return "";
            }

            int scannedCount = 0;
            do {
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
                int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

                long id = cursor.getLong(idIndex);
                String displayName = cursor.getString(nameIndex);
                long dateAddedMs = cursor.getLong(dateAddedIndex) * 1000;
                String filePath = cursor.getString(dataIndex);

                long timeDifference = Math.abs(callEndTimeMs - dateAddedMs);

                if (timeDifference < 120000) {
                    Uri contentUri = Uri.withAppendedPath(audioUri, String.valueOf(id));

                    if (filePath != null && filePath.contains("/Music/PhoneRecord/")) {
                        return contentUri.toString();
                    }

                    if (looksLikeCallRecording(displayName)) {
                        secondaryCandidates.add(new UriData(contentUri.toString(), filePath));
                    }
                }

                scannedCount++;
            } while (scannedCount < RECORDING_SCAN_LIMIT && cursor.moveToNext());

            if (!secondaryCandidates.isEmpty()) {
                return secondaryCandidates.get(0).uriString;
            }

        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private static class UriData {
        String uriString;
        String filePath;

        UriData(String uriString, String filePath) {
            this.uriString = uriString;
            this.filePath = filePath;
        }
    }

    private boolean looksLikeCallRecording(String displayName) {
        if (displayName == null) {
            return false;
        }
        String lowerName = displayName.toLowerCase(Locale.US);
        return lowerName.contains("call") || lowerName.contains("record") || lowerName.contains("voice");
    }
}