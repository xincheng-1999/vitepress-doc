package dev.learning.sessionlab;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private final TaskService service;
    TaskController(TaskService service) { this.service = service; }

    @PostMapping
    ResponseEntity<TaskResponse> create(Principal principal, @Valid @RequestBody CreateTaskRequest request) {
        var task = service.create(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
    }
    @GetMapping("/{id}")
    TaskResponse get(Principal principal, @PathVariable String id) {
        return service.get(principal.getName(), id);
    }
    @GetMapping
    TaskPage list(Principal principal, @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page 应为 0..10000，size 应为 1..100");
        }
        return service.list(principal.getName(), page, size);
    }
    @PutMapping("/{id}")
    TaskResponse update(Principal principal, @PathVariable String id,
                        @Valid @RequestBody UpdateTaskRequest request) {
        return service.update(principal.getName(), id, request);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Principal principal, @PathVariable String id) {
        service.delete(principal.getName(), id);
    }
}
