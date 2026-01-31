package me.cresterida;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RootRedirectFilter {

    Logger logger = LoggerFactory.getLogger(RootRedirectFilter.class);
    // Priority 5: Run BEFORE Quinoa (which usually runs at 10 or higher)
    public void register(@Observes Filters filters) {
        filters.register(this::handleRoot, 5);
    }

    private void handleRoot(RoutingContext rc) {
        String path = rc.normalizedPath();
        logger.info("Received request for path: " +  path);

        // JOB 1: Fix the URL (Browser Redirect 301)
        // If the user types "localhost:8080/whisper", we force them to ".../whisper/"
        // This ensures main.js is loaded from the correct relative path.
        if (path.equals("/whisper")) {
            rc.response()
                    .setStatusCode(301)
                    .putHeader("Location", "/whisper/")
                    .end();
            return;
        }

        // JOB 2: Serve the App (Internal Reroute)
        // If the user is at "/whisper/", we secretly serve them "/whisper/index.html".
        // The URL in the browser stays "/whisper/", but the content is the app.
        if (path.equals("/whisper/")) {
            logger.info("Rerouting to /whisper/index.html for path: " + path);
            // "reroute" tells Quarkus to restart processing with the new path
            rc.reroute("/whisper/index.html");
            return;
        }

        // If it's any other path (like /whisper/main.js), let Quarkus handle it normally
        rc.next();
    }
}

