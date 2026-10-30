package hu.kerekpar.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Minden API kérés naplózása: metódus, útvonal, felhasználó, státusz, időtartam. */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String USER_HEADER = "X-User-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} user={} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                    request.getHeader(USER_HEADER), response.getStatus(), millis);
        }
    }
}
