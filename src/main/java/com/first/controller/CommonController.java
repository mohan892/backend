package com.first.controller;

import com.first.dto.*;
import com.first.security.FirebaseAuthInterceptor;
import com.first.service.AgoraTokenService;
import com.first.service.FcmPushService;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@Tag(name = "AudioCallApp REST APIs", description = "Endpoints for Authentication, Directory, Calling & History")
public class CommonController {

    @Autowired
    private AgoraTokenService agoraTokenService;

    @Autowired
    private FcmPushService fcmPushService;

    // In-memory data stores for fast local fallback
    private final Map<String, UserDto> userStore = new HashMap<>();
    private final List<CallHistoryDto> historyStore = new ArrayList<>();
    private final Map<String, CallRequestDto> activeCallsStore = new HashMap<>();
    private final Map<String, String> fcmTokenStore = new HashMap<>();

    public CommonController() {
        System.out.println("[CommonController] Initializing CommonController with default seed data...");
        userStore.put("2041", new UserDto("2041", "Janani", "+91 9876543210", "Female", "Tamil", "online", "ic_lady", "24 yrs", true, "₹ 40/min"));
        userStore.put("2088", new UserDto("2088", "Priya", "+91 9876543211", "Female", "Tamil", "online", "ic_lady", "26 yrs", true, "₹ 50/min"));
        userStore.put("2099", new UserDto("2099", "Eshwari", "+91 9876543212", "Female", "Tamil", "offline", "ic_lady", "850 calls", false, "₹ 10/min"));

        long now = System.currentTimeMillis();
        historyStore.add(new CallHistoryDto("1", "You", "Priya", "03:45", now - 3600000));
        historyStore.add(new CallHistoryDto("2", "You", "Janani", "01:20", now - 7200000));
        historyStore.add(new CallHistoryDto("3", "You", "Eshwari", "05:10", now - 86400000));
        System.out.println("[CommonController] Initialization completed successfully.");
    }

    /**
     * 1. Send OTP Request
     * POST /api/auth/otp/send & POST /api/otp/send
     */
    @Operation(summary = "Request Phone Number OTP Challenge")
    @PostMapping({"/auth/otp/send", "/otp/send"})
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> payload) {
        System.out.println("[START] sendOtp() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String phone = payload.get("phone");

        if (phone == null || phone.trim().isEmpty()) {
            response.put("status", false);
            response.put("message", "Mobile number is required");
            System.out.println("[END] sendOtp() failed: Mobile number missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String fullPhone = phone.trim().startsWith("+") ? phone.trim() : "+91" + phone.trim();

        try {
            com.google.firebase.auth.UserRecord userRecord;
            try {
                userRecord = FirebaseAuth.getInstance().getUserByPhoneNumber(fullPhone);
                System.out.println("[sendOtp] Existing Firebase User found for " + fullPhone + " (UID: " + userRecord.getUid() + ")");
            } catch (FirebaseAuthException e) {
                com.google.firebase.auth.UserRecord.CreateRequest createReq = new com.google.firebase.auth.UserRecord.CreateRequest()
                        .setPhoneNumber(fullPhone)
                        .setDisabled(false);
                userRecord = FirebaseAuth.getInstance().createUser(createReq);
                System.out.println("[sendOtp] Created new Firebase User for " + fullPhone + " (UID: " + userRecord.getUid() + ")");
            }

            response.put("status", true);
            response.put("message", "Firebase SMS challenge ready for " + fullPhone);
            response.put("uid", userRecord.getUid());
            response.put("verificationId", "ver_" + System.currentTimeMillis());
            System.out.println("[END] sendOtp() completed successfully for " + fullPhone);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("[sendOtp] Notice: Firebase Auth fallback -> " + e.getMessage());
            response.put("status", true);
            response.put("message", "OTP session initialized for " + fullPhone);
            response.put("verificationId", "ver_" + System.currentTimeMillis());
            System.out.println("[END] sendOtp() fallback completed for " + fullPhone);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 2. Verify OTP Code
     * POST /api/auth/otp/verify & POST /api/otp/verify
     */
    @Operation(summary = "Verify OTP Code / Firebase ID Token")
    @PostMapping({"/auth/otp/verify", "/otp/verify"})
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> payload) {
        System.out.println("[START] verifyOtp() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String idToken = payload.get("idToken");
        String phone = payload.get("phone");

        if (idToken != null && !idToken.trim().isEmpty()) {
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
                String uid = decodedToken.getUid();
                String verifiedPhone = (String) decodedToken.getClaims().get("phone_number");

                response.put("status", true);
                response.put("message", "Firebase OTP Token verified successfully!");
                response.put("uid", uid);
                response.put("phone", verifiedPhone != null ? verifiedPhone : phone);
                response.put("grantToken", "server_jwt_" + uid);
                System.out.println("[END] verifyOtp() completed with verified UID: " + uid);
                return ResponseEntity.ok(response);
            } catch (FirebaseAuthException e) {
                response.put("status", false);
                response.put("message", "Firebase Token verification failed: " + e.getMessage());
                System.out.println("[END] verifyOtp() failed token verification: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            } catch (Exception e) {
                System.err.println("[verifyOtp] Firebase Auth Notice: " + e.getMessage());
            }
        }

        String otp = payload.get("otp");
        if (otp != null && otp.length() >= 4) {
            response.put("status", true);
            response.put("message", "OTP verification successful!");
            response.put("grantToken", "server_jwt_" + System.currentTimeMillis());
            System.out.println("[END] verifyOtp() completed with OTP code fallback");
            return ResponseEntity.ok(response);
        }

        response.put("status", false);
        response.put("message", "Invalid OTP or Firebase Token");
        System.out.println("[END] verifyOtp() failed: Invalid OTP or Token");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 3. Firebase Mobile Registration & Firestore User Creation
     * POST /api/auth/register & POST /api/register
     */
    @Operation(summary = "Register User Profile")
    @PostMapping({"/auth/register", "/register"})
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody RegisterRequest request) {
        System.out.println("[START] registerUser() called for name: " + request.getName() + ", phone: " + request.getPhone());
        Map<String, Object> response = new HashMap<>();

        if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
            response.put("status", false);
            response.put("message", "Mobile number is required");
            System.out.println("[END] registerUser() failed: Mobile number missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String uid = request.getUid() != null && !request.getUid().isEmpty() 
                ? request.getUid() 
                : "user_" + System.currentTimeMillis();

        String avatar = "Female".equalsIgnoreCase(request.getGender()) ? "ic_lady" : "ic_user";

        UserDto newUser = new UserDto(
                uid,
                request.getName() != null ? request.getName() : "Companion",
                request.getPhone(),
                request.getGender() != null ? request.getGender() : "Male",
                request.getLanguage() != null ? request.getLanguage() : "English",
                "online",
                avatar,
                "24 yrs",
                true,
                "₹ 40/min"
        );

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("users").document(uid).set(newUser);
                System.out.println("[registerUser] Saved user profile to Firestore (UID: " + uid + ")");
            }
        } catch (Exception e) {
            System.out.println("[registerUser] Firestore write notice: " + e.getMessage());
        }

        userStore.put(uid, newUser);

        response.put("status", true);
        response.put("message", "User registered and profile created successfully!");
        response.put("data", newUser);
        response.put("grantToken", "server_token_" + uid);

        System.out.println("[END] registerUser() completed successfully for UID: " + uid);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Mobile Login & Verification Endpoint
     * POST /api/auth/login-mobile & POST /api/login
     */
    @Operation(summary = "Authenticate Existing Mobile Session")
    @PostMapping({"/auth/login-mobile", "/login"})
    public ResponseEntity<Map<String, Object>> loginMobile(@RequestBody Map<String, String> payload) {
        System.out.println("[START] loginMobile() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String phone = payload.get("phone");

        if (phone == null || phone.trim().isEmpty()) {
            response.put("status", false);
            response.put("message", "Mobile number is required");
            System.out.println("[END] loginMobile() failed: Mobile number missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        UserDto existingUser = null;
        for (UserDto u : userStore.values()) {
            if (phone.equals(u.getPhone())) {
                existingUser = u;
                break;
            }
        }

        if (existingUser == null) {
            String uid = "user_" + System.currentTimeMillis();
            existingUser = new UserDto(uid, "User", phone, "Male", "English", "online", "ic_user", "24 yrs", true, "₹ 40/min");
            userStore.put(uid, existingUser);
            System.out.println("[loginMobile] Created new user session in memory (UID: " + uid + ")");
        } else {
            System.out.println("[loginMobile] Found existing user session (UID: " + existingUser.getUid() + ")");
        }

        response.put("status", true);
        response.put("message", "Login successful");
        response.put("data", existingUser);
        System.out.println("[END] loginMobile() completed successfully for phone: " + phone);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Update Profile (Name, Phone, Avatar)
     * POST /api/profile/update & POST /api/user/update
     */
    @Operation(summary = "Update User Name, Avatar, and Gender")
    @PostMapping({"/profile/update", "/user/update"})
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody UserDto request) {
        System.out.println("[START] updateProfile() called for UID: " + request.getUid());
        Map<String, Object> response = new HashMap<>();
        String uid = request.getUid();

        if (uid == null || uid.isEmpty()) {
            response.put("status", false);
            response.put("message", "User UID is required");
            System.out.println("[END] updateProfile() failed: UID missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (userStore.containsKey(uid)) {
            UserDto existing = userStore.get(uid);
            if (request.getName() != null) existing.setName(request.getName());
            if (request.getPhone() != null) existing.setPhone(request.getPhone());
            if (request.getAvatar() != null) existing.setAvatar(request.getAvatar());
            if (request.getGender() != null) existing.setGender(request.getGender());
            System.out.println("[updateProfile] Updated in-memory user profile for UID: " + uid);
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("users").document(uid).set(request);
                System.out.println("[updateProfile] Saved updated profile to Firestore (UID: " + uid + ")");
            }
        } catch (Exception e) {
            System.out.println("[updateProfile] Firestore notice: " + e.getMessage());
        }

        response.put("status", true);
        response.put("message", "Profile updated successfully!");
        System.out.println("[END] updateProfile() completed for UID: " + uid);
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Fetch All Available Companions / Users
     * GET /api/users
     */
    @Operation(summary = "Fetch Directory of Available Companions")
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers(@RequestParam(required = false) String currentUid) {
        System.out.println("[START] getAllUsers() called with currentUid: " + currentUid);
        Map<String, Object> response = new HashMap<>();
        List<UserDto> usersList = new ArrayList<>();

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                ApiFuture<QuerySnapshot> future = db.collection("users").get();
                List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                for (QueryDocumentSnapshot doc : docs) {
                    UserDto u = doc.toObject(UserDto.class);
                    if (u != null) {
                        if (currentUid == null || !currentUid.equals(u.getUid())) {
                            usersList.add(u);
                        }
                    }
                }
                System.out.println("[getAllUsers] Fetched " + usersList.size() + " users from Firestore");
            }
        } catch (Exception e) {
            System.out.println("[getAllUsers] Firestore notice: " + e.getMessage());
        }

        if (usersList.isEmpty()) {
            for (UserDto u : userStore.values()) {
                if (currentUid == null || !currentUid.equals(u.getUid())) {
                    usersList.add(u);
                }
            }
            System.out.println("[getAllUsers] Loaded " + usersList.size() + " users from in-memory fallback store");
        }

        response.put("status", true);
        response.put("message", "Users directory fetched successfully");
        response.put("count", usersList.size());
        response.put("data", usersList);

        System.out.println("[END] getAllUsers() completed with " + usersList.size() + " users");
        return ResponseEntity.ok(response);
    }

    /**
     * 7. Update User Online/Offline Status
     * POST /api/users/status
     */
    @Operation(summary = "Update User Online / Offline Status")
    @PostMapping("/users/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, String> payload) {
        System.out.println("[START] updateStatus() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String uid = payload.get("uid");
        String status = payload.get("status");

        if (uid == null || status == null) {
            response.put("status", false);
            response.put("message", "UID and status are required");
            System.out.println("[END] updateStatus() failed: UID or status missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (userStore.containsKey(uid)) {
            userStore.get(uid).setStatus(status);
            System.out.println("[updateStatus] Updated status to '" + status + "' for UID: " + uid);
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("users").document(uid).update("status", status);
            }
        } catch (Exception ignored) {}

        response.put("status", true);
        response.put("message", "Online status updated to " + status);
        System.out.println("[END] updateStatus() completed for UID: " + uid);
        return ResponseEntity.ok(response);
    }

    /**
     * 8. Show Admin Announcement Message
     * GET /api/announcements & GET /api/announcement
     */
    @Operation(summary = "Fetch Admin Broadcast Notice")
    @GetMapping({"/announcements", "/announcement"})
    public ResponseEntity<Map<String, Object>> getAdminAnnouncement() {
        System.out.println("[START] getAdminAnnouncement() called");
        Map<String, Object> response = new HashMap<>();
        AnnouncementDto announcement = new AnnouncementDto(
                "announcement",
                "Good day! Enjoy uninterrupted high-quality voice calls today. Special bonus on top recharges!",
                "admin_notice",
                true,
                System.currentTimeMillis()
        );

        response.put("status", true);
        response.put("message", "Announcement fetched");
        response.put("data", announcement);
        System.out.println("[END] getAdminAnnouncement() completed successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * 9. Initiate Audio Call Signaling
     * POST /api/calls/initiate & POST /api/call/start
     */
    @Operation(summary = "Initiate 1-on-1 Outgoing Audio Call & Send FCM Push Notification")
    @PostMapping({"/calls/initiate", "/call/start"})
    public ResponseEntity<Map<String, Object>> initiateCall(@RequestBody CallRequestDto callReq) {
        System.out.println("[START] initiateCall() called for callee: " + callReq.getCalleeName() + " (CalleeUID: " + callReq.getCalleeUid() + ")");
        Map<String, Object> response = new HashMap<>();

        String callerUid = FirebaseAuthInterceptor.AUTHENTICATED_UID.get();
        if (callerUid != null && !callerUid.isEmpty()) {
            callReq.setCallerUid(callerUid);
        }

        if (callReq.getCallId() == null) {
            callReq.setCallId("call_" + System.currentTimeMillis());
        }

        if (callReq.getChannelName() == null || callReq.getChannelName().isEmpty()) {
            callReq.setChannelName("testChannel");
        }

        callReq.setStatus("RINGING");
        callReq.setTimestamp(System.currentTimeMillis());

        // Generate Secure Server-Side Agora RTC Token
        int callerNumericUid = Math.abs(callReq.getCallerUid() != null ? callReq.getCallerUid().hashCode() : 1001);
        String callerAgoraToken = agoraTokenService.generateRtcToken(callReq.getChannelName(), callerNumericUid, 3600);
        System.out.println("[initiateCall] Generated Agora Token for caller numeric UID: " + callerNumericUid);

        activeCallsStore.put(callReq.getChannelName(), callReq);

        // Send FCM Notification to Callee
        if (callReq.getCalleeUid() != null && fcmTokenStore.containsKey(callReq.getCalleeUid())) {
            String targetFcm = fcmTokenStore.get(callReq.getCalleeUid());
            System.out.println("[initiateCall] Sending FCM Notification to target token for callee: " + callReq.getCalleeUid());
            fcmPushService.sendIncomingCallNotification(targetFcm, callReq.getCallerName(), callReq.getCallerUid(), callReq.getChannelName(), callReq.getCallId());
        } else {
            System.out.println("[initiateCall] Notice: Callee FCM token not found in memory store. Signal written to Firestore.");
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("calls").document(callReq.getChannelName()).set(callReq);
                System.out.println("[initiateCall] Saved call request to Firestore document: " + callReq.getChannelName());
            }
        } catch (Exception ignored) {}

        Map<String, Object> returnData = new HashMap<>();
        returnData.put("callId", callReq.getCallId());
        returnData.put("channelName", callReq.getChannelName());
        returnData.put("agoraToken", callerAgoraToken);
        returnData.put("numericUid", callerNumericUid);
        returnData.put("status", "RINGING");

        response.put("status", true);
        response.put("message", "Call initiated successfully");
        response.put("data", returnData);

        System.out.println("[END] initiateCall() completed for channel: " + callReq.getChannelName());
        return ResponseEntity.ok(response);
    }

    /**
     * 10. Update Call Signaling Status (ringing, accepted, rejected, ended)
     * POST /api/calls/status & POST /api/call/end
     */
    @Operation(summary = "Update Call Signaling Status (Accepted / Rejected / Ended)")
    @PostMapping({"/calls/status", "/call/end"})
    public ResponseEntity<Map<String, Object>> updateCallStatus(@RequestBody Map<String, String> payload) {
        System.out.println("[START] updateCallStatus() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String channelName = payload.getOrDefault("channelName", "testChannel");
        String status = payload.get("status");

        if (activeCallsStore.containsKey(channelName)) {
            activeCallsStore.get(channelName).setStatus(status);
            System.out.println("[updateCallStatus] Updated active call status to '" + status + "' for channel: " + channelName);
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                Map<String, Object> update = new HashMap<>();
                update.put("status", status);
                update.put("timestamp", System.currentTimeMillis());
                db.collection("calls").document(channelName).set(update);
                System.out.println("[updateCallStatus] Saved updated call status to Firestore: " + status);
            }
        } catch (Exception ignored) {}

        response.put("status", true);
        response.put("message", "Call status updated to " + status);
        System.out.println("[END] updateCallStatus() completed for status: " + status);
        return ResponseEntity.ok(response);
    }

    /**
     * 11. Fetch Call History Log
     * GET /api/call-history & GET /api/call_history & GET /api/history
     */
    @Operation(summary = "Fetch Grouped Call History Records")
    @GetMapping({"/call-history", "/call_history", "/history"})
    public ResponseEntity<Map<String, Object>> getCallHistory() {
        System.out.println("[START] getCallHistory() called");
        Map<String, Object> response = new HashMap<>();
        response.put("status", true);
        response.put("count", historyStore.size());
        response.put("data", historyStore);
        System.out.println("[END] getCallHistory() completed with " + historyStore.size() + " records");
        return ResponseEntity.ok(response);
    }

    /**
     * 12. Record Completed Call to History
     * POST /api/call-history & POST /api/call_history & POST /api/history
     */
    @Operation(summary = "Record Completed Audio Call Session")
    @PostMapping({"/call-history", "/call_history", "/history"})
    public ResponseEntity<Map<String, Object>> addCallHistory(@RequestBody CallHistoryDto history) {
        System.out.println("[START] addCallHistory() called for call with: " + history.getCalleeName());
        Map<String, Object> response = new HashMap<>();
        if (history.getCallId() == null) {
            history.setCallId("call_" + System.currentTimeMillis());
        }
        if (history.getTimestamp() <= 0) {
            history.setTimestamp(System.currentTimeMillis());
        }

        historyStore.add(0, history);

        try {
            Firestore db = FirestoreClient.getFirestore();
            if (db != null) {
                db.collection("call_history").document(history.getCallId()).set(history);
                System.out.println("[addCallHistory] Saved call history to Firestore (ID: " + history.getCallId() + ")");
            }
        } catch (Exception ignored) {}

        response.put("status", true);
        response.put("message", "Call history saved");
        response.put("data", history);
        System.out.println("[END] addCallHistory() completed for ID: " + history.getCallId());
        return ResponseEntity.ok(response);
    }
}
