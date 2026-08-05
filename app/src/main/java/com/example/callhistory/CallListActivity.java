package com.example.callhistory;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.List;

public class CallListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CallAdapter adapter;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        dbHelper = new DatabaseHelper(this);
        List<CallItem> callItems = loadCallsFromDb();
        
        adapter = new CallAdapter(callItems);
        recyclerView.setAdapter(adapter);
    }

    private List<CallItem> loadCallsFromDb() {
        List<CallItem> items = new ArrayList<>();
        Cursor cursor = dbHelper.getAllCalls();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                int phoneIdx = cursor.getColumnIndex(DatabaseHelper.COL_PHONE_NUMBER);
                int typeIdx = cursor.getColumnIndex(DatabaseHelper.COL_CALL_TYPE);
                int durIdx = cursor.getColumnIndex(DatabaseHelper.COL_TOTAL_DURATION_SECONDS);
                int dateIdx = cursor.getColumnIndex(DatabaseHelper.COL_START_TIME);

                String phone = phoneIdx >= 0 ? cursor.getString(phoneIdx) : "Unknown";
                String type = typeIdx >= 0 ? cursor.getString(typeIdx) : "Unknown";
                long duration = durIdx >= 0 ? cursor.getLong(durIdx) : 0;
                long dateMs = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0;

                items.add(new CallItem(phone, type, duration, dateMs));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return items;
    }

    private static class CallItem {
        String phone;
        String type;
        long duration;
        long dateMs;

        CallItem(String phone, String type, long duration, long dateMs) {
            this.phone = phone;
            this.type = type;
            this.duration = duration;
            this.dateMs = dateMs;
        }
    }

    private static class CallAdapter extends RecyclerView.Adapter<CallAdapter.CallViewHolder> {
        private final List<CallItem> items;

        CallAdapter(List<CallItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_record, parent, false);
            return new CallViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
            CallItem item = items.get(position);
            holder.tvContact.setText(item.phone);
            holder.tvDetails.setText("Type: " + item.type);
            holder.tvDuration.setText("Duration: " + item.duration + "s");
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class CallViewHolder extends RecyclerView.ViewHolder {
            TextView tvContact, tvDetails, tvDuration;
            CallViewHolder(View itemView) {
                super(itemView);
                tvContact = itemView.findViewById(R.id.tvContact);
                tvDetails = itemView.findViewById(R.id.tvDetails);
                tvDuration = itemView.findViewById(R.id.tvDuration);
            }
        }
    }
}
