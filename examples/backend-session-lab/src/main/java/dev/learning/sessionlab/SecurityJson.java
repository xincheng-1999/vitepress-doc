package dev.learning.sessionlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
class SecurityJson {
    private final ObjectMapper mapper;
    SecurityJson(ObjectMapper mapper) { this.mapper = mapper; }

    void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        write(response, status, new ApiError(code, message, MDC.get("requestId")));
    }
    void write(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), value);
    }
}
