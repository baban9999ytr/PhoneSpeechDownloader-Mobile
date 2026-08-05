package com.example.callhistory;

public class CallUploadPayload {
    public CallRecord record;
    public boolean has_media;
    public String audio_url;

    public CallUploadPayload(CallRecord record, boolean hasMedia,String audio_url) {
        this.record = record;
        this.has_media = hasMedia;
        this.audio_url= audio_url;
    }
}
