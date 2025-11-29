package com.exentricdev.clipshare.filter;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadRateLimitFilter extends OncePerRequestFilter {
    private final LoadingCache<String, Bucket> userBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(k -> newBucket());

    private static final Logger log = LoggerFactory.getLogger(UploadRateLimitFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Rate limit only upload requests
        try {
            String requestURI = request.getRequestURI();
            HttpMethod method = HttpMethod.valueOf(request.getMethod());

            // Only apply to POST /api/videos
            if (!(requestURI.equals("/api/videos") && method == HttpMethod.POST)) {
                filterChain.doFilter(request, response);
                return;
            }

            // If not authenticated, skip rate limiting
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                filterChain.doFilter(request, response);
                return;
            }

            String userId = authentication.getName();
            Bucket bucket = userBuckets.get(userId);

            if (bucket.tryConsume(1)) {
                log.info("Upload request allowed for user: {}", userId);
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Rate limit exceeded. Please try again later.");
            }
        } catch (Exception e) {
            log.error("Error in UploadRateLimitFilter: ", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private Bucket newBucket() {
        // Define your bucket configuration here
        return Bucket.builder()
                .addLimit(Bandwidth.simple(3, Duration.ofMinutes(1)))
                .build();
    }
}
