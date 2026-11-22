package com.first.AudioService;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@SpringBootApplication
@ComponentScan(basePackages = {"com.first"})
public class AudioServiceApplication {

	public static void main(String[] args) {
		// Prevent gRPC Netty tcnative JNI OpenSSL crashes on Linux/Alpine containers
		System.setProperty("io.grpc.netty.shaded.io.netty.handler.ssl.noNative", "true");
		SpringApplication.run(AudioServiceApplication.class, args);
	}

	
	@PostConstruct
	public void initFirebase() {
		try {
			if (FirebaseApp.getApps().isEmpty()) {
				ClassPathResource resource = new ClassPathResource("/etc/secrets/serviceAccountKey.json");
				if (resource.exists()) {
					try (InputStream serviceAccount = resource.getInputStream()) {
						String keyContent = new String(serviceAccount.readAllBytes());
						if (keyContent.contains("REPLACE_WITH_FIREBASE") || keyContent.contains("your-private-key") || keyContent.contains("replace_with")) {
							System.out.println("Notice: serviceAccountKey.json contains placeholders. To query live Firebase Firestore, replace with private key JSON from Firebase Console.");
							return;
						}
						InputStream stream = resource.getInputStream();
						FirebaseOptions options = FirebaseOptions.builder()
								.setCredentials(GoogleCredentials.fromStream(stream))
								.setProjectId("audiocallapp-6196e")
								.build();
						FirebaseApp.initializeApp(options);
						System.out.println("Firebase Admin SDK initialized successfully from serviceAccountKey.json!");
					} catch (Exception e) {
						System.out.println("Notice: Could not load serviceAccountKey.json credentials: " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Firebase initialization notice: " + e.getMessage());
		}
	}
}
