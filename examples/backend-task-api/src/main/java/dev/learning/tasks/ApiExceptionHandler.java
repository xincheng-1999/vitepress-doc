package dev.learning.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private ApiError error(String code, String message) {
        return new ApiError(code, message, MDC.get("requestId"));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> business(ApiException ex) {
        return ResponseEntity.status(ex.status).body(error(ex.code, ex.getMessage()));
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(error("INVALID_ARGUMENT", "标题须为非空文本且不超过 100 个 UTF-16 单元，更新时须提供合法状态"), headers, status);
    }
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Keep MVC's status and headers (e.g. Allow for 405), never turn everything into 500.
        String message = HttpStatus.valueOf(status.value()).getReasonPhrase();
        return new ResponseEntity<>(error("HTTP_" + status.value(), message), headers, status);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex) {
        LOG.error("unexpected request failure", ex);
        return ResponseEntity.internalServerError().body(error("INTERNAL_ERROR", "服务暂时异常，请提供 requestId 联系排查"));
    }
}
