package org.tauasa.faux.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Puts the security headers on every response, error pages included. The
 * page has no inline script or style, so the policy allows only the app's own
 * files. nginx adds Strict-Transport-Security, since only it knows the
 * connection is HTTPS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String CSP =
            "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        chain.doFilter(request, response);
    }

    /** Error pages are a separate dispatch; they get the headers too. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
