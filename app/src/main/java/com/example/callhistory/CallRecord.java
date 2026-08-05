package com.example.callhistory;

public class CallRecord {
    public String deviceName, deviceModel, osVersion, phoneNumber, contactName, callType;
    public int isIncoming, isOutgoing, isMissed, isRejected, isAnswered;
    public long startTime, answerTime, endTime;
    public long totalDurationSeconds, ringDurationSeconds, talkDurationSeconds;
    public String locationCoords;
    public int simSlot;
    public String carrierName, disconnectCause;

    public CallRecord() {}
}