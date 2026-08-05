package com.example.callhistory;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "CallTrackerExtended.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_NAME = "call_records";

    public static final String COL_ID = "id";
    public static final String COL_DEVICE_NAME = "device_name";
    public static final String COL_DEVICE_MODEL = "device_model";
    public static final String COL_DEVICE_OS_VERSION = "device_os_version";
    public static final String COL_PHONE_NUMBER = "phone_number";
    public static final String COL_CONTACT_NAME = "contact_name";
    public static final String COL_CALL_TYPE = "call_type";
    public static final String COL_IS_INCOMING = "is_incoming";
    public static final String COL_IS_OUTGOING = "is_outgoing";
    public static final String COL_IS_MISSED = "is_missed";
    public static final String COL_IS_REJECTED = "is_rejected";
    public static final String COL_IS_ANSWERED = "is_answered";
    public static final String COL_START_TIME = "start_time";
    public static final String COL_ANSWER_TIME = "answer_time";
    public static final String COL_END_TIME = "end_time";
    public static final String COL_TOTAL_DURATION_SECONDS = "total_duration_seconds";
    public static final String COL_RING_DURATION_SECONDS = "ring_duration_seconds";
    public static final String COL_TALK_DURATION_SECONDS = "talk_duration_seconds";
    public static final String COL_LOCATION_COORDS = "location_coords";
    public static final String COL_SIM_SLOT = "sim_slot";
    public static final String COL_CARRIER_NAME = "carrier_name";
    public static final String COL_DISCONNECT_CAUSE = "disconnect_cause";

    private final Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DEVICE_NAME + " TEXT, " +
                COL_DEVICE_MODEL + " TEXT, " +
                COL_DEVICE_OS_VERSION + " TEXT, " +
                COL_PHONE_NUMBER + " TEXT, " +
                COL_CONTACT_NAME + " TEXT, " +
                COL_CALL_TYPE + " TEXT, " +
                COL_IS_INCOMING + " INTEGER, " +
                COL_IS_OUTGOING + " INTEGER, " +
                COL_IS_MISSED + " INTEGER, " +
                COL_IS_REJECTED + " INTEGER, " +
                COL_IS_ANSWERED + " INTEGER, " +
                COL_START_TIME + " INTEGER, " +
                COL_ANSWER_TIME + " INTEGER, " +
                COL_END_TIME + " INTEGER, " +
                COL_TOTAL_DURATION_SECONDS + " INTEGER, " +
                COL_RING_DURATION_SECONDS + " INTEGER, " +
                COL_TALK_DURATION_SECONDS + " INTEGER, " +
                COL_LOCATION_COORDS + " TEXT, " +
                COL_SIM_SLOT + " INTEGER, " +
                COL_CARRIER_NAME + " TEXT, " +
                COL_DISCONNECT_CAUSE + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertCallRecordAndSync(String deviceName, String deviceModel, String osVersion, String number, String contactName,
                                        String type, int isIncoming, int isOutgoing, int isMissed, int isRejected, int isAnswered,
                                        long start, long answer, long end, long totalDur, long ringDur, long talkDur,
                                        String loc, int simSlot, String carrier, String disconnectCause, String audioFilePath) {
        CallRecord record = saveRecord(deviceName, deviceModel, osVersion, number, contactName, type, isIncoming, isOutgoing,
                isMissed, isRejected, isAnswered, start, answer, end, totalDur, ringDur, talkDur, loc, simSlot, carrier, disconnectCause);
        shipLogToServer(record, audioFilePath, false);
    }

    public boolean insertCallRecordAndSyncBlocking(String deviceName, String deviceModel, String osVersion, String number, String contactName,
                                                   String type, int isIncoming, int isOutgoing, int isMissed, int isRejected, int isAnswered,
                                                   long start, long answer, long end, long totalDur, long ringDur, long talkDur,
                                                   String loc, int simSlot, String carrier, String disconnectCause, String audioFilePath) {
        CallRecord record = saveRecord(deviceName, deviceModel, osVersion, number, contactName, type, isIncoming, isOutgoing,
                isMissed, isRejected, isAnswered, start, answer, end, totalDur, ringDur, talkDur, loc, simSlot, carrier, disconnectCause);
        return shipLogToServer(record, audioFilePath, true);
    }

    private CallRecord saveRecord(String deviceName, String deviceModel, String osVersion, String number, String contactName,
                                  String type, int isIncoming, int isOutgoing, int isMissed, int isRejected, int isAnswered,
                                  long start, long answer, long end, long totalDur, long ringDur, long talkDur,
                                  String loc, int simSlot, String carrier, String disconnectCause) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DEVICE_NAME, safe(deviceName));
        values.put(COL_DEVICE_MODEL, safe(deviceModel));
        values.put(COL_DEVICE_OS_VERSION, safe(osVersion));
        values.put(COL_PHONE_NUMBER, safe(number));
        values.put(COL_CONTACT_NAME, safe(contactName));
        values.put(COL_CALL_TYPE, safe(type));
        values.put(COL_IS_INCOMING, isIncoming);
        values.put(COL_IS_OUTGOING, isOutgoing);
        values.put(COL_IS_MISSED, isMissed);
        values.put(COL_IS_REJECTED, isRejected);
        values.put(COL_IS_ANSWERED, isAnswered);
        values.put(COL_START_TIME, start);
        values.put(COL_ANSWER_TIME, answer);
        values.put(COL_END_TIME, end);
        values.put(COL_TOTAL_DURATION_SECONDS, totalDur);
        values.put(COL_RING_DURATION_SECONDS, ringDur);
        values.put(COL_TALK_DURATION_SECONDS, talkDur);
        values.put(COL_LOCATION_COORDS, safe(loc));
        values.put(COL_SIM_SLOT, simSlot);
        values.put(COL_CARRIER_NAME, safe(carrier));
        values.put(COL_DISCONNECT_CAUSE, safe(disconnectCause));
        db.insert(TABLE_NAME, null, values);
        db.close();

        CallRecord record = new CallRecord();
        record.deviceName = safe(deviceName);
        record.deviceModel = safe(deviceModel);
        record.osVersion = safe(osVersion);
        record.phoneNumber = safe(number);
        record.contactName = safe(contactName);
        record.callType = safe(type);
        record.isIncoming = isIncoming;
        record.isOutgoing = isOutgoing;
        record.isMissed = isMissed;
        record.isRejected = isRejected;
        record.isAnswered = isAnswered;
        record.startTime = start;
        record.answerTime = answer;
        record.endTime = end;
        record.totalDurationSeconds = totalDur;
        record.ringDurationSeconds = ringDur;
        record.talkDurationSeconds = talkDur;
        record.locationCoords = safe(loc);
        record.simSlot = simSlot;
        record.carrierName = safe(carrier);
        record.disconnectCause = safe(disconnectCause);
        return record;
    }

    private boolean shipLogToServer(CallRecord record, String audioFilePath, boolean blocking) {
        SharedPreferences prefs = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        String serverUrl = prefs.getString("server_url", "http://10.0.2.2:5678/");
        String webhookMode = prefs.getString("webhook_mode", "production");
        String retrofitBaseUrl = getRetrofitBaseUrl(serverUrl);
        String uploadUrl = getUploadUrl(serverUrl, webhookMode);

        ApiService apiService = RetroFitClient.getClient(retrofitBaseUrl).create(ApiService.class);

        // audioFilePath is now our Supabase URL! Ensure it isn't null.
        String supabaseUrl = (audioFilePath != null) ? audioFilePath : "";
        boolean hasMedia = !supabaseUrl.isEmpty();

        // Create the JSON payload that includes the URL
        CallUploadPayload payload = new CallUploadPayload(record, hasMedia, supabaseUrl);

        // We only use uploadCallLogJson now. No more Multipart.
        Call<ResponseBody> call = apiService.uploadCallLogJson(uploadUrl, "qwe123qwe123", payload);

        if (blocking) {
            try {
                Response<ResponseBody> response = call.execute();
                Log.d("SYNC_RESULT", "HTTP " + response.code() + " to " + uploadUrl + " phone=" + record.phoneNumber);
                return response.isSuccessful();
            } catch (IOException e) {
                Log.e("SYNC_FAILURE", "Blocking network transport exception", e);
                return false;
            }
        }

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("SYNC_SUCCESS", "Network synchronization complete: " + uploadUrl + " phone=" + record.phoneNumber);
                    showToast("Successfully synced with n8n server!");
                } else {
                    Log.e("SYNC_REJECT", "Server returned " + response.code() + " for " + uploadUrl);
                    showToast("Failed to sync: Server error " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("SYNC_FAILURE", "Network transport exception", t);
                showToast("Network error syncing with n8n server");
            }
        });
        return true;
    }

    private String getRetrofitBaseUrl(String serverUrl) {
        Uri uri = Uri.parse(normalizeBaseUrl(serverUrl));
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String authority = uri.getEncodedAuthority();
        if (authority == null || authority.isEmpty()) {
            return "http://10.0.2.2:5678/";
        }
        return scheme + "://" + authority + "/";
    }

    private String getUploadUrl(String serverUrl, String webhookMode) {
        String normalized = normalizeBaseUrl(serverUrl);
        if (normalized.contains("/webhook/") || normalized.contains("/webhook-test/")) {
            return normalized;
        }
        String prefix = "test".equals(webhookMode) ? "webhook-test" : "webhook";
        return normalized + prefix + "/upload-logs";
    }

    private String normalizeBaseUrl(String url) {
        String normalized = url == null ? "" : url.trim();
        if (normalized.isEmpty()) normalized = "http://10.0.2.2:5678/";
        if (!normalized.endsWith("/")) normalized += "/";
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showToast(String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        );
    }

    public android.database.Cursor getAllCalls() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY " + COL_ID + " DESC", null);
    }
}