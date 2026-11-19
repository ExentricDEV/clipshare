package com.exentricdev.clipshare.filter;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Token-bucket rate limit on uploads: 3 per minute per user.
 *
 * Registered inside the Spring Security chain right after bearer-token authentication (see SecurityConfig).
 * It used to run before Spring Security as a plain servlet filter, where no user was known yet, so it never
 * limited anything.
 */
public class UploadRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(UploadRateLimitFilter.class);

    private final LoadingCache<String, Bucket> userBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(k -> newBucket());

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/videos".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // anonymous uploads are rejected by authorization further down the chain
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = userBuckets.get(authentication.getName());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.info("Upload rate limit hit for user {}", authentication.getName());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.getWriter().write("Rate limit exceeded. Please try again later.");
        }
    }

    private static Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(1)).build())
                .build();
    }
}
