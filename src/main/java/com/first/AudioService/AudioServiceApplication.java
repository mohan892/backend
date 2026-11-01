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
		SpringApplication.run(AudioServiceApplication.class, args);
	}

	@PostConstruct
	public void initFirebase() {
		try {
			if (FirebaseApp.getApps().isEmpty()) {
				ClassPathResource resource = new ClassPathResource("serviceAccountKey.json");
				if (resource.exists()) {
					try (InputStream serviceAccount = resource.getInputStream()) {
						FirebaseOptions options = FirebaseOptions.builder()
								.setCredentials(GoogleCredentials.fromStream(serviceAccount))
								.build();
						FirebaseApp.initializeApp(options);
						System.out.println("Firebase Admin SDK initialized successfully from serviceAccountKey.json!");
						return;
					} catch (Exception e) {
						System.out.println("Notice: serviceAccountKey.json needs your private key from Firebase Console. App running in standalone mode.");
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Firebase initialization notice: " + e.getMessage());
		}
	}
}
