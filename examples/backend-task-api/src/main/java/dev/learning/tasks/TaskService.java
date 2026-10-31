package dev.learning.tasks;

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
    public TaskResponse create(CreateTaskRequest request) {
        var task = new TaskResponse(UUID.randomUUID().toString(), normalizeTitle(request.title()),
            TaskStatus.TODO, Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository.insert(task);
        return task;
    }
    public TaskResponse get(String id) {
        return repository.find(id).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
    }
    public TaskPage list(int page, int size) {
        return new TaskPage(repository.list(page, size), repository.count(), page, size);
    }
    @Transactional
    public TaskResponse update(String id, UpdateTaskRequest request) {
        var normalized = new UpdateTaskRequest(normalizeTitle(request.title()), request.status());
        if (repository.update(id, normalized) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
        }
        return get(id);
    }
    public void delete(String id) { repository.delete(id); }

    private String normalizeTitle(String title) {
        // Hibernate's @NotBlank and String.strip() differ on Unicode whitespace.
        // Recheck the normalized value so EM SPACE cannot become an empty stored title.
        if (title == null || title.strip().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "标题不能为空白");
        }
        return title.strip();
    }
}
