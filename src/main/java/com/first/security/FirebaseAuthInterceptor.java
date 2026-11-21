package com.first.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class FirebaseAuthInterceptor implements HandlerInterceptor {

    public static final ThreadLocal<String> AUTHENTICATED_UID = new ThreadLocal<>();
    public static final ThreadLocal<String> AUTHENTICATED_PHONE = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("[START] FirebaseAuthInterceptor.preHandle() -> " + request.getMethod() + " " + request.getRequestURI());

        // Allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            System.out.println("[END] FirebaseAuthInterceptor.preHandle() -> OPTIONS preflight allowed.");
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String idToken = authHeader.substring(7);
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
                AUTHENTICATED_UID.set(decodedToken.getUid());
                AUTHENTICATED_PHONE.set((String) decodedToken.getClaims().get("phone_number"));
                System.out.println("[END] FirebaseAuthInterceptor.preHandle() -> Verified Firebase Token for UID: " + decodedToken.getUid());
                return true;
            } catch (Exception e) {
                String devUid = request.getHeader("X-Dev-Uid");
                if (devUid != null && !devUid.isEmpty()) {
                    AUTHENTICATED_UID.set(devUid);
                    System.out.println("[END] FirebaseAuthInterceptor.preHandle() -> Dev UID Header used: " + devUid);
                    return true;
                }
            }
        }

        String devUid = request.getHeader("X-Dev-Uid");
        if (devUid != null && !devUid.isEmpty()) {
            AUTHENTICATED_UID.set(devUid);
            System.out.println("[END] FirebaseAuthInterceptor.preHandle() -> Dev UID Header used: " + devUid);
            return true;
        }

        System.out.println("[END] FirebaseAuthInterceptor.preHandle() -> Allowed request.");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AUTHENTICATED_UID.remove();
        AUTHENTICATED_PHONE.remove();
    }
}
