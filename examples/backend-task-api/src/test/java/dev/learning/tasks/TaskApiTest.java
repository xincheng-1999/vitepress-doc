package dev.learning.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:unused-test-bootstrap",
    "spring.sql.init.mode=never", "spring.profiles.active=test"
})
@AutoConfigureMockMvc
@Import(TaskApiTest.IsolatedDatabase.class)
class TaskApiTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:task-api-tests-" + UUID.randomUUID();

    @TestConfiguration(proxyBeanMethods = false)
    static class IsolatedDatabase {
        @Bean DataSource dataSource() throws Exception {
            // An unbound bean cannot inherit external datasource/Hikari connection settings.
            var source = new DriverManagerDataSource();
            source.setDriverClassName("org.h2.Driver");
            source.setUrl(DATABASE_URL + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
            source.setUsername("sa");
            source.setPassword("");
            try (var connection = source.getConnection()) {
                assertThat(connection.getMetaData().getURL()).isEqualTo(DATABASE_URL);
            }
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
            return source;
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean() throws Exception {
        // Fail closed before destructive setup, even if a future config overrides test properties.
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:").isEqualTo(DATABASE_URL);
        }
        jdbc.update("DELETE FROM tasks");
    }

    String create() throws Exception {
        var response = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Learn SQL\"}"))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.status").value("TODO")).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("id").asText();
    }
    @Test void createReadListUpdateDelete() throws Exception {
        String id = create();
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Learn SQL"));
        mvc.perform(get("/api/tasks?page=0&size=10")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Done\",\"status\":\"DONE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }
    @Test void invalidTitleNeverWrites() throws Exception {
        for (String title : new String[]{"", "   ", "\u2003", "\u2003 \u2003", "x".repeat(101)}) {
            mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("title", title))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isZero();
        String id = create();
        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("title", "\u2003", "status", "DONE"))))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(jsonPath("$.title").value("Learn SQL"));
    }
    @Test void malformedJsonAndInvalidEnumAre400() throws Exception {
        for (String body : new String[]{"{\"title\":123}", "{\"title\":1.5}", "{\"title\":true}", "{\"title\":null}", "{}", "{\"title\":[]}"}) {
            mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        for (String body : new String[]{"{\"title\":\"x\",\"status\":\"INVALID\"}", "{\"title\":\"x\",\"status\":0}"}) {
            mvc.perform(put("/api/tasks/missing").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }
    @Test void pageBoundariesAreEnforced() throws Exception {
        for (String query : new String[]{"size=0", "size=101", "page=-1", "page=10001", "page=nope"}) {
            mvc.perform(get("/api/tasks?" + query)).andExpect(status().isBadRequest());
        }
    }
    @Test void missingUpdateIs404() throws Exception {
        mvc.perform(put("/api/tasks/missing").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"x\",\"status\":\"TODO\"}"))
            .andExpect(status().isNotFound());
    }
    @Test void protocolErrorsKeepTheirStatus() throws Exception {
        mvc.perform(patch("/api/tasks/missing")).andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow"));
        mvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/no-such-route")).andExpect(status().isNotFound());
    }
    @Test void sqlParametersDoNotBecomeSql() throws Exception {
        String title = "Robert'); DROP TABLE tasks;--";
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("title", title))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(title));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isEqualTo(1);
    }
}
