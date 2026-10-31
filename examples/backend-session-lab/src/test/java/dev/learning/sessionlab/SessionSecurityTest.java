package dev.learning.sessionlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.profiles.active=test", "spring.sql.init.mode=never",
    "server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:unused-test-bootstrap"
})
@AutoConfigureMockMvc
@Import(SessionSecurityTest.IsolatedDatabase.class)
class SessionSecurityTest {
    private static final String DATABASE_NAME = "session-security-tests-" + UUID.randomUUID();
    private static final String DATABASE_URL = "jdbc:h2:mem:" + DATABASE_NAME;

    @TestConfiguration(proxyBeanMethods = false)
    static class IsolatedDatabase {
        @Bean
        DataSource dataSource() throws Exception {
            // A manually constructed, unbound bean bypasses external datasource/Hikari settings.
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
    @Autowired PasswordEncoder encoder;
    @Autowired UserDetailsService users;

    @BeforeEach
    void cleanOnlyIsolatedMemoryDatabase() throws Exception {
        // Fail closed BEFORE DELETE, even if a future refactor changes datasource setup.
        try (var connection = jdbc.getDataSource().getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url).startsWith("jdbc:h2:mem:").isEqualTo(DATABASE_URL);
        }
        jdbc.update("DELETE FROM tasks");
    }

    record Browser(MockHttpSession session, String token, String header) {}

    Browser csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
            .andExpect(jsonPath("$.parameterName").value("_csrf")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        return new Browser((MockHttpSession) result.getRequest().getSession(false),
            body.get("token").asText(), body.get("headerName").asText());
    }

    Browser login(String username) throws Exception {
        Browser before = csrf(null);
        var result = mvc.perform(post("/api/auth/login").session(before.session())
                .header(before.header(), before.token())
                .param("username", username).param("password", username + "-local-demo-17"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username))
            .andExpect(header().doesNotExist("Location")).andReturn();
        return csrf((MockHttpSession) result.getRequest().getSession(false));
    }

    String create(Browser browser, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/tasks").session(browser.session())
                .header(browser.header(), browser.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", title))))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("TODO")).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        return id;
    }

    @Test void anonymousRequestsAreJson401AndUidCannotAuthenticate() throws Exception {
        var result = mvc.perform(get("/api/tasks").header("uid", "alice"))
            .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }

    @Test void csrfEndpointIsPublicAndCreatesSessionWithoutCorsPermission() throws Exception {
        Browser browser = csrf(null);
        assertThat(browser.session()).isNotNull();
        mvc.perform(get("/api/auth/csrf").session(browser.session()).header("Origin", "https://untrusted.example"))
            .andExpect(status().isOk()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/tasks").header(browser.header(), browser.token()))
            .andExpect(status().isUnauthorized()); // CSRF token alone is not authentication.
    }

    @Test void passwordsAreBcryptHashesCheckedThroughEncoder() {
        for (String username : new String[]{"alice", "bob"}) {
            String hash = users.loadUserByUsername(username).getPassword();
            assertThat(hash).startsWith("$2").isNotEqualTo(username + "-local-demo-17");
            assertThat(encoder.matches(username + "-local-demo-17", hash)).isTrue();
            assertThat(encoder.matches("wrong", hash)).isFalse();
        }
    }

    @Test void successfulLoginChangesSessionIdAndRequiresFreshCsrf() throws Exception {
        Browser before = csrf(null);
        String oldId = before.session().getId();
        var result = mvc.perform(post("/api/auth/login").session(before.session())
                .header(before.header(), before.token())
                .param("username", "alice").param("password", "alice-local-demo-17"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(oldId);
        mvc.perform(post("/api/tasks").session(session).header(before.header(), before.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"stale token\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        Browser after = csrf(session);
        create(after, "fresh token");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isEqualTo(1);
    }

    @Test void wrongPasswordAndUnknownUserAreJson401WithoutAuthentication() throws Exception {
        Browser browser = csrf(null);
        for (String username : new String[]{"alice", "not-a-user"}) {
            mvc.perform(post("/api/auth/login").session(browser.session())
                    .header(browser.header(), browser.token())
                    .param("username", username).param("password", "wrong-password"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }
        mvc.perform(get("/api/tasks").session(browser.session())).andExpect(status().isUnauthorized());
    }

    @Test void loginAlsoRequiresValidCsrf() throws Exception {
        Browser browser = csrf(null);
        for (String token : new String[]{"", "invalid"}) {
            mvc.perform(post("/api/auth/login").session(browser.session()).header(browser.header(), token)
                    .param("username", "alice").param("password", "alice-local-demo-17"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        mvc.perform(get("/api/tasks").session(browser.session())).andExpect(status().isUnauthorized());
    }

    @Test void ownerCanCreateReadUpdateAndDelete() throws Exception {
        Browser alice = login("alice");
        String id = create(alice, " Learn security ");
        assertThat(jdbc.queryForObject("SELECT owner_id FROM tasks WHERE id=?", String.class, id)).isEqualTo("alice");
        mvc.perform(get("/api/tasks/{id}", id).session(alice.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Learn security"))
            .andExpect(jsonPath("$.ownerId").doesNotExist()).andExpect(jsonPath("$.createdAt").isNotEmpty());
        mvc.perform(put("/api/tasks/{id}", id).session(alice.session()).header(alice.header(), alice.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Done\",\"status\":\"DONE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(delete("/api/tasks/{id}", id).session(alice.session()).header(alice.header(), alice.token()))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(delete("/api/tasks/{id}", id).session(alice.session()).header(alice.header(), alice.token()))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks/{id}", id).session(alice.session())).andExpect(status().isNotFound());
    }

    @Test void foreignReadUpdateAndDeleteAre404AndCannotAlterData() throws Exception {
        Browser alice = login("alice");
        Browser bob = login("bob");
        String id = create(alice, "Alice private");
        mvc.perform(get("/api/tasks/{id}", id).session(bob.session()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mvc.perform(put("/api/tasks/{id}", id).session(bob.session()).header(bob.header(), bob.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Stolen\",\"status\":\"DONE\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/{id}", id).session(bob.session()).header(bob.header(), bob.token()))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks/{id}", id).session(alice.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Alice private"))
            .andExpect(jsonPath("$.status").value("TODO"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isEqualTo(1);
    }

    @Test void paginationItemsAndTotalAreScopedToPrincipal() throws Exception {
        Browser alice = login("alice");
        Browser bob = login("bob");
        create(alice, "A1");
        create(alice, "A2");
        String bobId = create(bob, "B1");
        for (int page : new int[]{0, 1}) {
            mvc.perform(get("/api/tasks?page=" + page + "&size=1").session(alice.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.page").value(page));
        }
        mvc.perform(get("/api/tasks?page=2&size=1").session(alice.session()))
            .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/tasks?ownerId=alice").session(bob.session()).header("uid", "alice"))
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(bobId))
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));
    }

    @Test void forgedUidAndOwnerFieldsCannotChangeOwnership() throws Exception {
        Browser alice = login("alice");
        Browser bob = login("bob");
        var result = mvc.perform(post("/api/tasks").session(alice.session()).header("uid", "bob")
                .header(alice.header(), alice.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Still Alice\",\"ownerId\":\"bob\",\"owner_id\":\"bob\",\"uid\":\"bob\"}"))
            .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(jdbc.queryForObject("SELECT owner_id FROM tasks WHERE id=?", String.class, id)).isEqualTo("alice");
        mvc.perform(get("/api/tasks/{id}", id).session(bob.session()).header("uid", "alice"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").session(bob.session())).andExpect(jsonPath("$.total").value(0));
    }

    @Test void everyMutationAndLogoutRejectMissingCsrfWithoutSideEffects() throws Exception {
        Browser alice = login("alice");
        String id = create(alice, "Unchanged");
        mvc.perform(post("/api/tasks").session(alice.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"No token\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/tasks/{id}", id).session(alice.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"No token\",\"status\":\"DONE\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/tasks/{id}", id).session(alice.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(alice.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks/{id}", id).session(alice.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Unchanged"))
            .andExpect(jsonPath("$.status").value("TODO"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isEqualTo(1);
    }

    @Test void csrfTokenFromAnotherSessionIsRejected() throws Exception {
        Browser alice = login("alice");
        Browser bob = login("bob");
        mvc.perform(post("/api/tasks").session(alice.session()).header(bob.header(), bob.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"cross-session\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isZero();
    }

    @Test void logoutInvalidatesSessionAndOldSessionCannotAccessTasks() throws Exception {
        Browser alice = login("alice");
        create(alice, "Persistent until app restart");
        mvc.perform(post("/api/auth/logout").session(alice.session()).header(alice.header(), alice.token()))
            .andExpect(status().isNoContent()).andExpect(content().string(""))
            .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(alice.session().isInvalid()).isTrue();
        mvc.perform(get("/api/tasks").session(alice.session()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isEqualTo(1);
    }

    @Test void strictJsonAndUnicodeWhitespaceCannotWriteOrOverwriteTasks() throws Exception {
        Browser alice = login("alice");
        for (String body : new String[]{"{", "{\"title\":123}", "{\"title\":1.5}", "{\"title\":true}",
                "{\"title\":null}", "{}", "{\"title\":[]}", "{\"title\":\"\\u2003\"}",
                json.writeValueAsString(Map.of("title", "x".repeat(101)))}) {
            mvc.perform(post("/api/tasks").session(alice.session()).header(alice.header(), alice.token())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Long.class)).isZero();
        String id = create(alice, "Keep me");
        for (String body : new String[]{"{\"title\":\"x\",\"status\":0}",
                "{\"title\":\"x\",\"status\":\"INVALID\"}", "{\"title\":\"\\u2003\",\"status\":\"DONE\"}"}) {
            mvc.perform(put("/api/tasks/{id}", id).session(alice.session()).header(alice.header(), alice.token())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/tasks/{id}", id).session(alice.session()))
            .andExpect(jsonPath("$.title").value("Keep me")).andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test void mvcProtocolErrorsKeepStatusAndHeadersAfterAuthentication() throws Exception {
        Browser alice = login("alice");
        mvc.perform(patch("/api/tasks/missing").session(alice.session()).header(alice.header(), alice.token()))
            .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"));
        mvc.perform(post("/api/tasks").session(alice.session()).header(alice.header(), alice.token())
                .contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/no-such-route").session(alice.session())).andExpect(status().isNotFound());
        for (String query : new String[]{"size=0", "size=101", "page=-1", "page=10001", "page=nope"}) {
            mvc.perform(get("/api/tasks?" + query).session(alice.session())).andExpect(status().isBadRequest());
        }
    }

    @Test void sqlLikeTitleIsStoredAsDataUnderAuthenticatedOwner() throws Exception {
        Browser bob = login("bob");
        String title = "Robert'); DROP TABLE tasks;--";
        String id = create(bob, title);
        mvc.perform(get("/api/tasks/{id}", id).session(bob.session()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value(title));
        assertThat(jdbc.queryForObject("SELECT owner_id FROM tasks WHERE id=?", String.class, id)).isEqualTo("bob");
    }
}
