package dev.learning.sessionlab;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {
    @GetMapping("/api/auth/csrf")
    ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        // getToken forces deferred token loading and creates a session for an anonymous caller.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName()));
    }
}
