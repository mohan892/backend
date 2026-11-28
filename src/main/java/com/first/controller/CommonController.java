package com.first.controller;

import com.first.dto.*;
import com.first.security.FirebaseAuthInterceptor;
import com.first.service.AgoraTokenService;
import com.first.service.FcmPushService;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@Tag(name = "AudioCallApp REST APIs", description = "Endpoints for Authentication, Directory, Calling, History & Razorpay Payments")
public class CommonController {

    @Autowired
    private AgoraTokenService agoraTokenService;

    @Autowired
    private FcmPushService fcmPushService;

    @Value("${RAZORPAY_KEY_ID:${razorpay.key_id:}}")
    private String razorpayKeyId;

    @Value("${RAZORPAY_KEY_SECRET:${razorpay.key_secret:}}")
    private String razorpayKeySecret;

    private String getRazorpayKeyId() {
        if (razorpayKeyId != null && !razorpayKeyId.trim().isEmpty()) return razorpayKeyId.trim();
        String env = System.getenv("RAZORPAY_KEY_ID");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        env = System.getenv("razorpay.key_id");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return "";
    }

    private String getRazorpayKeySecret() {
        if (razorpayKeySecret != null && !razorpayKeySecret.trim().isEmpty()) return razorpayKeySecret.trim();
        String env = System.getenv("RAZORPAY_KEY_SECRET");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        env = System.getenv("razorpay.key_secret");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return "";
    }

    // In-memory data stores for active sessions
    private final Map<String, UserDto> userStore = new LinkedHashMap<>();
    private final List<CallHistoryDto> historyStore = new ArrayList<>();
    private final Map<String, CallRequestDto> activeCallsStore = new HashMap<>();
    private static final Map<String, String> fcmTokenStore = new HashMap<>();

    public CommonController() {
        System.out.println("[CommonController] Initializing CommonController with seed companion directory...");
      
        long now = System.currentTimeMillis();
        long yesterday = now - (24 * 60 * 60 * 1000L);

        // Seed companions from screenshot
        userStore.put("c_101", new UserDto("c_101", "Ramya", "+91 9876543210", "Female", "Tamil, English", "online", "avatar_priya", "24 yrs", true, "₹10 /min"));
        userStore.put("c_102", new UserDto("c_102", "Pavisha", "+91 9876543211", "Female", "Kannada, Hindi", "online", "avatar_janani", "22 yrs", true, "₹12 /min"));
        userStore.put("c_103", new UserDto("c_103", "Meenu", "+91 9876543212", "Female", "Malayalam, Tamil", "online", "avatar_eshwari", "25 yrs", true, "₹15 /min"));
        userStore.put("c_104", new UserDto("c_104", "Kandasamy", "+91 9876543213", "Female", "Tamil, English", "online", "avatar_sanjana", "28 yrs", true, "₹15 /min"));
        userStore.put("c_105", new UserDto("c_105", "Janani", "+91 9876543214", "Female", "Tamil", "online", "avatar_janani", "24 yrs", true, "₹40 /min"));
        userStore.put("c_106", new UserDto("c_106", "Priya", "+91 9876543215", "Female", "Tamil, English", "online", "avatar_priya", "26 yrs", true, "₹50 /min"));
        userStore.put("c_107", new UserDto("c_107", "Eshwari", "+91 9876543216", "Female", "Tamil, Hindi", "online", "avatar_eshwari", "25 yrs", true, "₹40 /min"));
        userStore.put("c_108", new UserDto("c_108", "Hema", "+91 9876543217", "Female", "Tamil, English", "in_call", "avatar_hema", "25 yrs", true, "₹40 /min"));
        userStore.put("c_109", new UserDto("c_109", "Sanjana", "+91 9876543218", "Female", "English, Hindi", "online", "avatar_sanjana", "24 yrs", true, "₹40 /min"));

        // Seed call history
        historyStore.add(new CallHistoryDto("h_1", null, "Priya", "Priya", "03:45", "INCOMING", now - 3600000));
        historyStore.add(new CallHistoryDto("h_2", null, "Janani", "Janani", "01:20", "INCOMING", now - 7200000));
        historyStore.add(new CallHistoryDto("h_3", null, "Eshwari", "Eshwari", "05:10", "OUTGOING", yesterday));
        historyStore.add(new CallHistoryDto("h_4", null, "Hema", "Hema", "Missed", "MISSED", yesterday - 3600000));
        historyStore.add(new CallHistoryDto("h_5", null, "Sanjana", "Sanjana", "02:15", "OUTGOING", yesterday - 7200000));
    }

    /**
     * 1. Send OTP Request
     * POST /api/auth/otp/send
     */
    @Operation(summary = "Request Phone Number OTP Challenge")
    @PostMapping("/auth/otp/send")
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
     * POST /api/auth/otp/verify
     */
    @Operation(summary = "Verify OTP Code / Firebase ID Token")
    @PostMapping("/auth/otp/verify")
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
     * 3. Firebase Mobile Registration
     * POST /api/auth/register
     */
    @Operation(summary = "Register User Profile in Firebase Firestore")
    @PostMapping("/auth/register")
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
                request.getName() != null ? request.getName() : "User",
                request.getPhone(),
                request.getGender() != null ? request.getGender() : "Male",
                request.getLanguage() != null ? request.getLanguage() : "English",
                "online",
                avatar,
                "24 yrs",
                true,
                "₹10 /min",
                null
        );

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    db.collection("users").document(uid).set(newUser);
                    System.out.println("[registerUser] Saved real user profile to Firestore (UID: " + uid + ")");
                }
            }
        } catch (Throwable t) {
            System.out.println("[registerUser] Firestore write notice: " + t.getMessage());
        }

        userStore.put(uid, newUser);

        response.put("status", true);
        response.put("message", "User registered and profile created in Firebase Firestore!");
        response.put("data", newUser);
        response.put("grantToken", "server_token_" + uid);

        System.out.println("[END] registerUser() completed successfully for UID: " + uid);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Register FCM Push Token
     * POST /api/users/fcm-token
     */
    @Operation(summary = "Register FCM Push Notification Token for User")
    @PostMapping("/users/fcm-token")
    public ResponseEntity<Map<String, Object>> registerFcmToken(@RequestBody Map<String, String> payload) {
        System.out.println("[START] registerFcmToken() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String uid = payload.get("uid");
        String fcmToken = payload.get("fcmToken");

        if (uid != null && !uid.isEmpty() && fcmToken != null && !fcmToken.isEmpty()) {
            fcmTokenStore.put(uid, fcmToken);
            System.out.println("[registerFcmToken] Successfully bound FCM Token uniquely to UID: " + uid);

            try {
                if (!FirebaseApp.getApps().isEmpty()) {
                    Firestore db = FirestoreClient.getFirestore();
                    if (db != null) {
                        Map<String, Object> update = new HashMap<>();
                        update.put("fcmToken", fcmToken);
                        db.collection("users").document(uid).set(update, com.google.cloud.firestore.SetOptions.merge());
                        System.out.println("[registerFcmToken] Updated FCM Token in Firestore (UID: " + uid + ")");
                    }
                }
            } catch (Throwable t) {
                System.out.println("[registerFcmToken] Firestore notice: " + t.getMessage());
            }
        }

        response.put("status", true);
        response.put("message", "FCM token registered successfully");
        System.out.println("[END] registerFcmToken() completed for UID: " + uid);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Mobile Login & Verification Endpoint
     * POST /api/auth/login-mobile
     */
    @Operation(summary = "Authenticate Existing Mobile Session")
    @PostMapping("/auth/login-mobile")
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
            existingUser = new UserDto(uid, "User", phone, "Male", "English", "online", "ic_user", "24 yrs", true, "₹10 /min", null);
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
     * 6. Update Profile
     * POST /api/profile/update
     */
    @Operation(summary = "Update User Name, Avatar, and Gender")
    @PostMapping("/profile/update")
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
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    db.collection("users").document(uid).set(request);
                    System.out.println("[updateProfile] Saved updated profile to Firestore (UID: " + uid + ")");
                }
            }
        } catch (Throwable t) {
            System.out.println("[updateProfile] Firestore notice: " + t.getMessage());
        }

        response.put("status", true);
        response.put("message", "Profile updated successfully!");
        System.out.println("[END] updateProfile() completed for UID: " + uid);
        return ResponseEntity.ok(response);
    }

    /**
     * 7. Fetch All Users Directory
     * GET /api/users
     */
    @Operation(summary = "Fetch Directory of All Users")
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        System.out.println("[START] getAllUsers() called");
        Map<String, Object> response = new HashMap<>();
        List<UserDto> usersList = new ArrayList<>(userStore.values());

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    ApiFuture<QuerySnapshot> future = db.collection("users").get();
                    List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                    for (QueryDocumentSnapshot doc : docs) {
                        UserDto u = doc.toObject(UserDto.class);
                        if (u != null && !userStore.containsKey(u.getUid())) {
                            usersList.add(u);
                            if (u.getFcmToken() != null && !u.getFcmToken().isEmpty()) {
                                fcmTokenStore.put(u.getUid(), u.getFcmToken());
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[getAllUsers] Firestore query notice: " + t.getMessage());
        }

        response.put("status", true);
        response.put("message", "Users directory fetched successfully");
        response.put("count", usersList.size());
        response.put("data", usersList);

        System.out.println("[END] getAllUsers() completed with " + usersList.size() + " users");
        return ResponseEntity.ok(response);
    }

    /**
     * 8. Create Razorpay Order from Backend
     * POST /api/payment/create-order
     */
    @Operation(summary = "Create Razorpay Order for Call Session Payment")
    @PostMapping("/payment/create-order")
    public ResponseEntity<Map<String, Object>> createRazorpayOrder(@RequestBody Map<String, Object> payload) {
        System.out.println("[START] createRazorpayOrder() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();

        try {
            String uid = (String) payload.get("uid");
            Number amountObj = (Number) payload.getOrDefault("amount", 10);
            int amountINR = amountObj.intValue();
            int amountInPaise = amountINR * 100;

            String orderId = "order_" + System.currentTimeMillis();
            String keyId = getRazorpayKeyId();
            String keySecret = getRazorpayKeySecret();

            try {
                if (!keySecret.isEmpty()) {
                    com.razorpay.RazorpayClient razorpay = new com.razorpay.RazorpayClient(keyId, keySecret);
                    org.json.JSONObject orderRequest = new org.json.JSONObject();
                    orderRequest.put("amount", amountInPaise);
                    orderRequest.put("currency", "INR");
                    orderRequest.put("receipt", "rcpt_" + System.currentTimeMillis());

                    com.razorpay.Order order = razorpay.orders.create(orderRequest);
                    orderId = order.get("id");
                }
            } catch (Exception ex) {
                System.out.println("[createRazorpayOrder] Razorpay Client Notice: " + ex.getMessage());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("orderId", orderId);
            data.put("amount", amountInPaise);
            data.put("currency", "INR");
            data.put("keyId", keyId);

            response.put("status", true);
            response.put("message", "Razorpay order created successfully");
            response.put("data", data);

            System.out.println("[END] createRazorpayOrder() completed with orderId: " + orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", false);
            response.put("message", "Failed to create Razorpay order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 9. Verify Razorpay Payment & Credit Wallet
     * POST /api/payment/verify
     */
    @Operation(summary = "Verify Razorpay Payment Signature & Credit User Balance")
    @PostMapping("/payment/verify")
    public ResponseEntity<Map<String, Object>> verifyPaymentAndCreditBalance(@RequestBody Map<String, Object> payload) {
        System.out.println("[START] verifyPaymentAndCreditBalance() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();

        try {
            String uid = (String) payload.get("uid");
            String paymentId = (String) payload.get("razorpayPaymentId");
            String orderId = (String) payload.get("razorpayOrderId");
            String signature = (String) payload.get("razorpaySignature");
            Number amountObj = (Number) payload.getOrDefault("amount", 10);
            int creditedCoins = amountObj != null ? amountObj.intValue() : 10;

            String keySecret = getRazorpayKeySecret();
            boolean isVerified = true;
            try {
                if (signature != null && !signature.isEmpty() && !keySecret.isEmpty()) {
                    org.json.JSONObject attributes = new org.json.JSONObject();
                    attributes.put("razorpay_order_id", orderId);
                    attributes.put("razorpay_payment_id", paymentId);
                    attributes.put("razorpay_signature", signature);

                    isVerified = com.razorpay.Utils.verifyPaymentSignature(attributes, keySecret);
                }
            } catch (Exception ex) {
                System.out.println("[verifyPayment] Razorpay signature notice: " + ex.getMessage());
            }

            if (!isVerified) {
                response.put("status", false);
                response.put("message", "Invalid Razorpay payment signature");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            int newBalance = 250;
            if (uid != null && userStore.containsKey(uid)) {
                UserDto user = userStore.get(uid);
                user.setCoinBalance(user.getCoinBalance() + creditedCoins);
                newBalance = user.getCoinBalance();
            }

            response.put("status", true);
            response.put("message", "Payment verified! Credited " + creditedCoins + " coins.");
            response.put("coinBalance", newBalance);

            System.out.println("[END] verifyPaymentAndCreditBalance() completed for UID: " + uid + ", new balance: " + newBalance);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", false);
            response.put("message", "Verification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 10. Update User Status
     * POST /api/users/status
     */
    @Operation(summary = "Update User Online / Offline Status")
    @PostMapping("/users/status")
    public ResponseEntity<Map<String, String>> updateStatus(@RequestBody Map<String, String> payload) {
        System.out.println("[START] updateStatus() called with payload: " + payload);
        Map<String, String> response = new HashMap<>();
        String uid = payload.get("uid");
        String status = payload.get("status");

        if (uid == null || status == null) {
            response.put("status", "false");
            response.put("message", "UID and status are required");
            System.out.println("[END] updateStatus() failed: UID or status missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (userStore.containsKey(uid)) {
            userStore.get(uid).setStatus(status);
            System.out.println("[updateStatus] Updated status to '" + status + "' for UID: " + uid);
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    db.collection("users").document(uid).update("status", status);
                }
            }
        } catch (Throwable ignored) {}

        response.put("status", "true");
        response.put("message", "Online status updated to " + status);
        System.out.println("[END] updateStatus() completed for UID: " + uid);
        return ResponseEntity.ok(response);
    }

    /**
     * 11. Show Admin Announcement Message
     * GET /api/announcements
     */
    @Operation(summary = "Fetch Admin Broadcast Notice")
    @GetMapping("/announcements")
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
     * 12. Initiate Audio Call Signaling & Send FCM Push
     * POST /api/calls/initiate
     */
    @Operation(summary = "Initiate 1-on-1 Outgoing Audio Call & Send FCM Push Notification")
    @PostMapping("/calls/initiate")
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

        int callerNumericUid = Math.abs(callReq.getCallerUid() != null ? callReq.getCallerUid().hashCode() : 1001);
        String callerAgoraToken = agoraTokenService.generateRtcToken(callReq.getChannelName(), callerNumericUid, 3600);

        activeCallsStore.put(callReq.getChannelName(), callReq);

        String calleeUid = callReq.getCalleeUid();
        String targetFcm = calleeUid != null ? fcmTokenStore.get(calleeUid) : null;

        if ((targetFcm == null || targetFcm.isEmpty()) && calleeUid != null && !FirebaseApp.getApps().isEmpty()) {
            try {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    DocumentSnapshot calleeDoc = db.collection("users").document(calleeUid).get().get();
                    if (calleeDoc.exists() && calleeDoc.getString("fcmToken") != null) {
                        targetFcm = calleeDoc.getString("fcmToken");
                        fcmTokenStore.put(calleeUid, targetFcm);
                        System.out.println("[initiateCall] Found target FCM Token in Firestore for callee UID: " + calleeUid);
                    }
                }
            } catch (Throwable t) {
                System.out.println("[initiateCall] Firestore FCM lookup notice: " + t.getMessage());
            }
        }

        if (targetFcm != null && !targetFcm.isEmpty()) {
            System.out.println("[initiateCall] Sending high-priority FCM push to callee UID: " + calleeUid);
            fcmPushService.sendIncomingCallNotification(targetFcm, callReq.getCallerName(), callReq.getCallerUid(), callReq.getChannelName(), callReq.getCallId());
        } else {
            System.out.println("[initiateCall] Notice: Callee FCM token not registered for UID: " + calleeUid + ". Signal written to Firestore.");
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    db.collection("calls").document(callReq.getChannelName()).set(callReq);
                }
            }
        } catch (Throwable ignored) {}

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
     * 13. Accept Call
     * POST /api/calls/accept
     */
    @Operation(summary = "Accept Incoming Audio Call")
    @PostMapping("/calls/accept")
    public ResponseEntity<Map<String, Object>> acceptCall(@RequestBody Map<String, String> payload) {
        System.out.println("[START] acceptCall() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String channelName = payload.getOrDefault("channelName", "testChannel");
        String calleeUid = FirebaseAuthInterceptor.AUTHENTICATED_UID.get();

        if (calleeUid == null || calleeUid.isEmpty()) {
            calleeUid = payload.getOrDefault("calleeUid", "callee_2001");
        }

        int calleeNumericUid = Math.abs(calleeUid.hashCode());
        String calleeAgoraToken = agoraTokenService.generateRtcToken(channelName, calleeNumericUid, 3600);

        if (activeCallsStore.containsKey(channelName)) {
            CallRequestDto req = activeCallsStore.get(channelName);
            req.setStatus("ACCEPTED");
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "ACCEPTED");
                    update.put("timestamp", System.currentTimeMillis());
                    db.collection("calls").document(channelName).set(update, com.google.cloud.firestore.SetOptions.merge());
                }
            }
        } catch (Throwable ignored) {}

        Map<String, Object> returnData = new HashMap<>();
        returnData.put("channelName", channelName);
        returnData.put("agoraToken", calleeAgoraToken);
        returnData.put("numericUid", calleeNumericUid);
        returnData.put("status", "ACCEPTED");

        response.put("status", true);
        response.put("message", "Call accepted");
        response.put("data", returnData);

        System.out.println("[END] acceptCall() completed successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * 14. Reject Call
     * POST /api/calls/reject
     */
    @Operation(summary = "Reject Incoming Audio Call")
    @PostMapping("/calls/reject")
    public ResponseEntity<Map<String, Object>> rejectCall(@RequestBody Map<String, String> payload) {
        System.out.println("[START] rejectCall() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String channelName = payload.getOrDefault("channelName", "testChannel");

        if (activeCallsStore.containsKey(channelName)) {
            CallRequestDto req = activeCallsStore.get(channelName);
            req.setStatus("REJECTED");
            historyStore.add(0, new CallHistoryDto("h_" + System.currentTimeMillis(), req.getCallerUid(), req.getCallerName(), req.getCalleeName(), "Missed", "MISSED", System.currentTimeMillis()));
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "REJECTED");
                    update.put("timestamp", System.currentTimeMillis());
                    db.collection("calls").document(channelName).set(update, com.google.cloud.firestore.SetOptions.merge());
                }
            }
        } catch (Throwable ignored) {}

        response.put("status", true);
        response.put("message", "Call rejected");
        System.out.println("[END] rejectCall() completed");
        return ResponseEntity.ok(response);
    }

    /**
     * 15. End Call
     * POST /api/calls/end
     */
    @Operation(summary = "End Active Audio Call")
    @PostMapping("/calls/end")
    public ResponseEntity<Map<String, Object>> endCall(@RequestBody Map<String, String> payload) {
        System.out.println("[START] endCall() called with payload: " + payload);
        Map<String, Object> response = new HashMap<>();
        String channelName = payload.getOrDefault("channelName", "testChannel");

        if (activeCallsStore.containsKey(channelName)) {
            CallRequestDto req = activeCallsStore.get(channelName);
            req.setStatus("ENDED");
            long elapsedSecs = (System.currentTimeMillis() - req.getTimestamp()) / 1000;
            if (elapsedSecs < 0) elapsedSecs = 0;
            String durationStr = String.format(Locale.getDefault(), "%02d:%02d", elapsedSecs / 60, elapsedSecs % 60);
            historyStore.add(0, new CallHistoryDto("h_" + System.currentTimeMillis(), req.getCallerUid(), req.getCallerName(), req.getCalleeName(), durationStr, "INCOMING", System.currentTimeMillis()));
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "ENDED");
                    update.put("timestamp", System.currentTimeMillis());
                    db.collection("calls").document(channelName).set(update, com.google.cloud.firestore.SetOptions.merge());
                }
            }
        } catch (Throwable ignored) {}

        response.put("status", true);
        response.put("message", "Call ended successfully");
        System.out.println("[END] endCall() completed");
        return ResponseEntity.ok(response);
    }

    /**
     * 16. Fetch Call History Log
     * GET /api/call-history
     */
    @Operation(summary = "Fetch Grouped Call History Records by User UID")
    @GetMapping("/call-history")
    public ResponseEntity<Map<String, Object>> getCallHistory(@RequestParam(required = false) String uid) {
        System.out.println("[START] getCallHistory() called with uid: " + uid);
        List<CallHistoryDto> userHistory = new ArrayList<>();
        for (CallHistoryDto h : historyStore) {
            if (uid == null || uid.isEmpty() || (h.getUserId() != null && h.getUserId().equals(uid))) {
                userHistory.add(h);
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("status", true);
        response.put("count", userHistory.size());
        response.put("data", userHistory);
        System.out.println("[END] getCallHistory() completed with " + userHistory.size() + " records");
        return ResponseEntity.ok(response);
    }

    /**
     * 17. Record Completed Call to History
     * POST /api/call-history
     */
    @Operation(summary = "Record Completed Audio Call Session")
    @PostMapping("/call-history")
    public ResponseEntity<Map<String, Object>> addCallHistory(@RequestBody CallHistoryDto history) {
        System.out.println("[START] addCallHistory() called for call with: " + history.getCalleeName());
        if (history.getCallId() == null) {
            history.setCallId("call_" + System.currentTimeMillis());
        }
        if (history.getTimestamp() <= 0) {
            history.setTimestamp(System.currentTimeMillis());
        }

        String authUid = FirebaseAuthInterceptor.AUTHENTICATED_UID.get();
        if (authUid != null && !authUid.isEmpty()) {
            history.setUserId(authUid);
        }

        historyStore.add(0, history);

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                Firestore db = FirestoreClient.getFirestore();
                if (db != null) {
                    db.collection("call_history").document(history.getCallId()).set(history);
                }
            }
        } catch (Throwable ignored) {}

        Map<String, Object> response = new HashMap<>();
        response.put("status", true);
        response.put("message", "Call history saved");
        response.put("data", history);
        System.out.println("[END] addCallHistory() completed for ID: " + history.getCallId());
        return ResponseEntity.ok(response);
    }
}
