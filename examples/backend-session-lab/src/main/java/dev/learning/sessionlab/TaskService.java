package dev.learning.sessionlab;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
class TaskService {
    private final TaskRepository repository;
    TaskService(TaskRepository repository) { this.repository = repository; }

    @Transactional
    public TaskResponse create(String owner, CreateTaskRequest request) {
        var task = new TaskResponse(UUID.randomUUID().toString(), normalizeTitle(request.title()),
            TaskStatus.TODO, Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository.insert(owner, task);
        return task;
    }
    public TaskResponse get(String owner, String id) {
        return repository.find(owner, id).orElseThrow(this::notFound);
    }
    public TaskPage list(String owner, int page, int size) {
        return new TaskPage(repository.list(owner, page, size), repository.count(owner), page, size);
    }
    @Transactional
    public TaskResponse update(String owner, String id, UpdateTaskRequest request) {
        var normalized = new UpdateTaskRequest(normalizeTitle(request.title()), request.status());
        if (repository.update(owner, id, normalized) == 0) throw notFound();
        return get(owner, id);
    }
    public void delete(String owner, String id) {
        // Missing and foreign resources share the same 404, without an unscoped lookup.
        if (repository.delete(owner, id) == 0) throw notFound();
    }
    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
    }
    private String normalizeTitle(String title) {
        // @NotBlank and strip() have different Unicode whitespace semantics.
        if (title == null || title.strip().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "标题不能为空白");
        }
        return title.strip();
    }
}
