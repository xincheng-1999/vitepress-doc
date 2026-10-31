package dev.learning.sessionlab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

enum TaskStatus { TODO, DOING, DONE }
record CreateTaskRequest(@NotBlank @Size(max = 100) String title) {}
record UpdateTaskRequest(@NotBlank @Size(max = 100) String title,
                         @NotNull TaskStatus status) {}
record TaskResponse(String id, String title, TaskStatus status, Instant createdAt) {}
record TaskPage(List<TaskResponse> items, long total, int page, int size) {}
record ApiError(String code, String message, String requestId) {}
record CsrfResponse(String token, String headerName, String parameterName) {}
