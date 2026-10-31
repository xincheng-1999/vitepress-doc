package dev.learning.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// Package-private records keep this tiny example readable. Larger projects may split them.
enum TaskStatus { TODO, DOING, DONE }
record CreateTaskRequest(@NotBlank @Size(max = 100) String title) {}
record UpdateTaskRequest(@NotBlank @Size(max = 100) String title,
                         @NotNull TaskStatus status) {}
record TaskResponse(String id, String title, TaskStatus status, Instant createdAt) {}
record TaskPage(List<TaskResponse> items, long total, int page, int size) {}
record ApiError(String code, String message, String requestId) {}
