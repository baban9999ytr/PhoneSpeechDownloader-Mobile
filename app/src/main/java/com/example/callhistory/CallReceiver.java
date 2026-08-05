package com.example.callhistory;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Locale;

public class CallReceiver extends BroadcastReceiver {

    private static final int RECORDING_SCAN_LIMIT = 20;
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;
    private static long timeRingingStart = 0;
    private static long timeAnswered = 0;
    private static boolean isIncoming = false;
    private static String savedNumber = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        String stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (number != null && !number.isEmpty()) {
            savedNumber = number;
        }

        int state;
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(stateStr)) {
            state = TelephonyManager.CALL_STATE_RINGING;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(stateStr)) {
            state = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(stateStr)) {
            state = TelephonyManager.CALL_STATE_IDLE;
        } else {
            return;
        }

        onCallStateChanged(context, state, savedNumber);
    }

    private void onCallStateChanged(Context context, int state, String number) {
        if (lastState == state) return;
        long currentTime = System.currentTimeMillis();

        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                isIncoming = true;
                timeRingingStart = currentTime;
                timeAnswered = 0;
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = true;
                    timeAnswered = currentTime;
                } else {
                    isIncoming = false;
                    timeRingingStart = currentTime;
                    timeAnswered = currentTime;
                }
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                if (timeRingingStart <= 0) {
                    break;
                }
                saveCompletedCall(context, number, currentTime);
                timeRingingStart = 0;
                timeAnswered = 0;
                savedNumber = "";
                break;

            default:
                break;
        }
        lastState = state;
    }

    private void saveCompletedCall(Context context, String number, long timeEnded) {
        long ringDuration = 0;
        long talkDuration = 0;
        long totalDuration = Math.max(0, (timeEnded - timeRingingStart) / 1000);

        int isIn = isIncoming ? 1 : 0;
        int isOut = !isIncoming ? 1 : 0;
        int isMissed = 0;
        int isRejected = 0;
        int isAnswered = 0;

        if (isIncoming) {
            if (timeAnswered > 0) {
                ringDuration = Math.max(0, (timeAnswered - timeRingingStart) / 1000);
                talkDuration = Math.max(0, (timeEnded - timeAnswered) / 1000);
                isAnswered = 1;
            } else {
                ringDuration = Math.max(0, (timeEnded - timeRingingStart) / 1000);
                if (ringDuration < 2) {
                    isRejected = 1;
                } else {
                    isMissed = 1;
                }
            }
        } else if (timeAnswered > 0) {
            talkDuration = Math.max(0, (timeEnded - timeAnswered) / 1000);
            isAnswered = 1;
        }

        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String carrierName = tm != null ? tm.getNetworkOperatorName() : "";
        String recordingUri = findRecentCallRecording(context, timeEnded);

        if (recordingUri.isEmpty()) {
            Log.d("CALL_RECEIVER", "No call recording found in MediaStore near this call.");
        }

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        dbHelper.insertCallRecordAndSync(
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.RELEASE,
                number == null ? "" : number,
                "Unknown",
                isIncoming ? "INCOMING" : "OUTGOING",
                isIn,
                isOut,
                isMissed,
                isRejected,
                isAnswered,
                timeRingingStart,
                timeAnswered,
                timeEnded,
                totalDuration,
                ringDuration,
                talkDuration,
                "40.1885,29.0610",
                0,
                carrierName,
                "NORMAL",
                recordingUri
        );
    }

    private String findRecentCallRecording(Context context, long callEndTimeMs) {
        ContentResolver resolver = context.getContentResolver();
        Uri audioUri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED
        };
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = resolver.query(audioUri, projection, null, null, sortOrder)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return "";
            }

            int scannedCount = 0;
            do {
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                long id = cursor.getLong(idIndex);
                String displayName = cursor.getString(nameIndex);
                long dateAddedMs = cursor.getLong(dateAddedIndex) * 1000;
                long timeDifference = Math.abs(callEndTimeMs - dateAddedMs);

                if (timeDifference < 120000 && looksLikeCallRecording(displayName)) {
                    String uri = Uri.withAppendedPath(audioUri, String.valueOf(id)).toString();
                    Log.d("CALL_RECORD", "Probable call recording found: " + uri);
                    return uri;
                }

                scannedCount++;
            } while (scannedCount < RECORDING_SCAN_LIMIT && cursor.moveToNext());
        } catch (Exception e) {
            Log.e("CALL_RECORD", "Error searching for audio files", e);
        }

        return "";
    }

    private boolean looksLikeCallRecording(String displayName) {
        if (displayName == null) {
            return false;
        }
        String lowerName = displayName.toLowerCase(Locale.US);
        return lowerName.contains("call") || lowerName.contains("record") || lowerName.contains("voice");
    }
}
