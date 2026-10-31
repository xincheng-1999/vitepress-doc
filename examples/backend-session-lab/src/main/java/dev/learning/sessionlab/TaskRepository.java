package dev.learning.sessionlab;

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
        rs.getString("id"), rs.getString("title"), TaskStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant());

    TaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void insert(String owner, TaskResponse task) {
        jdbc.update("INSERT INTO tasks(id,owner_id,title,status,created_at) VALUES (?,?,?,?,?)",
            task.id(), owner, task.title(), task.status().name(), Timestamp.from(task.createdAt()));
    }
    Optional<TaskResponse> find(String owner, String id) {
        return jdbc.query("SELECT id,title,status,created_at FROM tasks WHERE owner_id = ? AND id = ?",
            mapper, owner, id).stream().findFirst();
    }
    List<TaskResponse> list(String owner, int page, int size) {
        return jdbc.query("SELECT id,title,status,created_at FROM tasks WHERE owner_id = ? "
            + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", mapper, owner, size, page * size);
    }
    long count(String owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE owner_id = ?", Long.class, owner);
    }
    int update(String owner, String id, UpdateTaskRequest request) {
        return jdbc.update("UPDATE tasks SET title = ?, status = ? WHERE owner_id = ? AND id = ?",
            request.title(), request.status().name(), owner, id);
    }
    int delete(String owner, String id) {
        return jdbc.update("DELETE FROM tasks WHERE owner_id = ? AND id = ?", owner, id);
    }
}
