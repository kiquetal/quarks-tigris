package me.cresterida.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private static final int CLEANUP_INTERVAL_MINUTES = 5;

    // Simple map: passphrase -> email (for demo - replace with database in production)
    private final Map<String, String> passphraseToEmail = new ConcurrentHashMap<>();

    // Session storage: sessionToken -> SessionData
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();
    private ScheduledExecutorService cleanupExecutor;

    public static class SessionData {
        private final String email;
        private final Instant createdAt;
        private Instant lastAccessedAt;

        public SessionData(String email) {
            this.email = email;
            this.createdAt = Instant.now();
            this.lastAccessedAt = Instant.now();
        }

        public String getEmail() {
            return email;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getLastAccessedAt() {
            return lastAccessedAt;
        }

        public void updateLastAccessed() {
            this.lastAccessedAt = Instant.now();
        }

        public boolean isExpired() {
            return Instant.now().isAfter(lastAccessedAt.plusSeconds(SESSION_TIMEOUT_MINUTES * 60L));
        }
    }

    void onStart(@Observes StartupEvent ev) {
        // Initialize demo passphrase->email mappings
        initializeDemoUsers();

        // Start background cleanup task
        cleanupExecutor = Executors.newScheduledThreadPool(1);
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        logger.info("Session manager started - {} demo users registered", passphraseToEmail.size());
    }

    /**
     * Initialize demo users (replace with database lookup in production)
     */
    private void initializeDemoUsers() {
        // Demo mappings: passphrase -> email
        passphraseToEmail.put("demo123", "demo@example.com");
        passphraseToEmail.put("test123", "test@example.com");
        passphraseToEmail.put("admin123", "admin@example.com");
        passphraseToEmail.put("your-secret-passphrase", "kiquetal@gmail.com");
    }

    /**
     * Validates passphrase and returns associated email
     * @param passphrase User's passphrase
     * @return Email if valid, null otherwise
     */
    public String validatePassphrase(String passphrase) {
        return passphraseToEmail.get(passphrase);
    }

    /**
     * Retrieves passphrase for a given email (reverse lookup)
     * @param email User's email
     * @return Passphrase if found, null otherwise
     */
    public String getPassphraseForEmail(String email) {
        // Reverse lookup: find passphrase for email
        for (Map.Entry<String, String> entry : passphraseToEmail.entrySet()) {
            if (entry.getValue().equals(email)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Registers a new passphrase->email mapping (for demo purposes)
     */
    public void registerUser(String passphrase, String email) {
        passphraseToEmail.put(passphrase, email);
        logger.info("Registered new user: {}", email);
    }

    /**
     * Creates a new session after successful passphrase validation
     * @param email User's email
     * @return Session token (cryptographically secure random string)
     */
    public String createSession(String email) {
        // Generate cryptographically secure session token
        byte[] tokenBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(tokenBytes);
        String sessionToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Store session
        SessionData sessionData = new SessionData(email);
        sessions.put(sessionToken, sessionData);

        logger.info("Session created for email: {} (token: {}...)", email, sessionToken.substring(0, 8));
        return sessionToken;
    }

    /**
     * Validates session token and returns associated email
     * @param sessionToken Session token from request
     * @return Email if session is valid, null otherwise
     */
    public String validateSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isEmpty()) {
            return null;
        }

        SessionData sessionData = sessions.get(sessionToken);
        if (sessionData == null) {
            logger.warn("Invalid session token: {}...", sessionToken.substring(0, Math.min(8, sessionToken.length())));
            return null;
        }

        if (sessionData.isExpired()) {
            logger.info("Session expired for email: {}", sessionData.getEmail());
            sessions.remove(sessionToken);
            return null;
        }

        // Update last accessed time
        sessionData.updateLastAccessed();
        return sessionData.getEmail();
    }

    /**
     * Destroys a session (logout)
     * @param sessionToken Session token to destroy
     */
    public void destroySession(String sessionToken) {
        SessionData removed = sessions.remove(sessionToken);
        if (removed != null) {
            logger.info("Session destroyed for email: {}", removed.getEmail());
        }
    }

    /**
     * Background cleanup of expired sessions
     */
    private void cleanupExpiredSessions() {
        int removed = 0;
        for (Map.Entry<String, SessionData> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired sessions", removed);
        }
    }

    /**
     * Get active session count (for monitoring)
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }


}
