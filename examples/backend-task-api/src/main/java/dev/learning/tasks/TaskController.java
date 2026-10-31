package dev.learning.tasks;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private final TaskService service;
    TaskController(TaskService service) { this.service = service; }

    @PostMapping
    ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        var task = service.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
    }
    @GetMapping("/{id}")
    TaskResponse get(@PathVariable String id) { return service.get(id); }
    @GetMapping
    TaskPage list(@RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page 应为 0..10000，size 应为 1..100");
        }
        return service.list(page, size);
    }
    @PutMapping("/{id}")
    TaskResponse update(@PathVariable String id, @Valid @RequestBody UpdateTaskRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { service.delete(id); }
}
