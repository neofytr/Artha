package com.artha.api.rest;

import com.artha.core.idempotency.IdempotencyKey;
import com.artha.core.idempotency.IdempotencyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;

/**
 * Server-side idempotency enforcement for POST/PUT requests.
 * <p>
 * Clients send an {@code Idempotency-Key} header (typically a UUID) that
 * uniquely identifies a logical operation. If we've already completed this
 * key, we replay the cached response. If a concurrent request with the same
 * key is in flight, we return 409. Otherwise we process normally and cache
 * the response.
 * <p>
 * TTL is a few hours — long enough for aggressive retries, short enough to
 * keep the store bounded. The key is per-API-path to avoid collisions if a
 * client reuses the same UUID across different endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyStore store;

    public IdempotencyFilter(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!isMutating(req)) {
            chain.doFilter(req, res);
            return;
        }
        String raw = req.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        IdempotencyKey key = new IdempotencyKey(raw + ":" + req.getMethod() + ":" + req.getRequestURI());
        IdempotencyStore.Reservation reservation = store.reserve(key, TTL);

        switch (reservation.state()) {
            case NEW -> processAndCache(req, res, chain, key);
            case IN_PROGRESS -> {
                res.setStatus(HttpServletResponse.SC_CONFLICT);
                res.getWriter().write("{\"error\":\"Request with same Idempotency-Key is in flight\"}");
            }
            case COMPLETED -> reservation.cachedResponse().ifPresentOrElse(
                    bytes -> {
                        try {
                            res.setStatus(HttpServletResponse.SC_OK);
                            res.setContentType("application/json");
                            res.getOutputStream().write(bytes);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    () -> { try { chain.doFilter(req, res); } catch (Exception e) { throw new RuntimeException(e); } });
        }
    }

    private void processAndCache(HttpServletRequest req, HttpServletResponse res, FilterChain chain,
                                 IdempotencyKey key) throws IOException, ServletException {
        CapturingResponse wrapper = new CapturingResponse(res);
        try {
            chain.doFilter(req, wrapper);
            if (wrapper.getStatus() < 500) {
                byte[] body = wrapper.getCapturedBody();
                wrapper.flushToClient();
                store.complete(key, body);
            } else {
                store.release(key);
                wrapper.flushToClient();
            }
        } catch (RuntimeException e) {
            store.release(key);
            throw e;
        }
    }

    private boolean isMutating(HttpServletRequest req) {
        String method = req.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    /** Buffers the response so we can both cache it and stream it to the client. */
    private static final class CapturingResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final jakarta.servlet.ServletOutputStream outputStream;
        private final PrintWriter writer;
        private final HttpServletResponse delegate;

        CapturingResponse(HttpServletResponse response) {
            super(response);
            this.delegate = response;
            this.outputStream = new jakarta.servlet.ServletOutputStream() {
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
                @Override public void write(int b) { buffer.write(b); }
            };
            this.writer = new PrintWriter(buffer);
        }

        @Override public jakarta.servlet.ServletOutputStream getOutputStream() { return outputStream; }
        @Override public PrintWriter getWriter() { return writer; }

        byte[] getCapturedBody() {
            writer.flush();
            return buffer.toByteArray();
        }

        void flushToClient() throws IOException {
            byte[] body = getCapturedBody();
            delegate.setContentLength(body.length);
            delegate.getOutputStream().write(body);
            delegate.getOutputStream().flush();
        }
    }
}
