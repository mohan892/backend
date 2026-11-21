package com.first.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FcmPushService {

    public boolean sendIncomingCallNotification(String targetFcmToken, String callerName, String callerUid, String channelName, String callId) {
        System.out.println("[START] FcmPushService.sendIncomingCallNotification() called for caller: " + callerName + ", channel: " + channelName);

        if (targetFcmToken == null || targetFcmToken.isEmpty()) {
            System.out.println("[END] FcmPushService.sendIncomingCallNotification() notice: Target FCM token empty. Call state saved in Firestore.");
            return false;
        }

        try {
            Message message = Message.builder()
                    .setToken(targetFcmToken)
                    .setNotification(Notification.builder()
                            .setTitle("Incoming Audio Call")
                            .setBody((callerName != null ? callerName : "Someone") + " is calling you...")
                            .build())
                    .putData("type", "INCOMING_CALL")
                    .putData("callId", callId)
                    .putData("callerName", callerName != null ? callerName : "Someone")
                    .putData("callerUid", callerUid != null ? callerUid : "")
                    .putData("channelName", channelName)
                    .putData("status", "RINGING")
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("[END] FcmPushService.sendIncomingCallNotification() FCM Notification sent successfully: " + response);
            return true;
        } catch (Exception e) {
            System.err.println("[END] FcmPushService.sendIncomingCallNotification() FCM Push Notice: " + e.getMessage());
            return false;
        }
    }
}
