package dev.learning.tasks;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
class TaskRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<TaskResponse> mapper = (rs, row) -> new TaskResponse(
        rs.getString("id"), rs.getString("title"),
        TaskStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant());

    TaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void insert(TaskResponse task) {
        jdbc.update("INSERT INTO tasks(id,title,status,created_at) VALUES (?,?,?,?)",
            task.id(), task.title(), task.status().name(), Timestamp.from(task.createdAt()));
    }
    Optional<TaskResponse> find(String id) {
        return jdbc.query("SELECT id,title,status,created_at FROM tasks WHERE id = ?", mapper, id)
            .stream().findFirst();
    }
    List<TaskResponse> list(int page, int size) {
        return jdbc.query("SELECT id,title,status,created_at FROM tasks ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            mapper, size, page * size);
    }
    long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class); }
    int update(String id, UpdateTaskRequest request) {
        return jdbc.update("UPDATE tasks SET title = ?, status = ? WHERE id = ?",
            request.title().strip(), request.status().name(), id);
    }
    void delete(String id) { jdbc.update("DELETE FROM tasks WHERE id = ?", id); }
}
