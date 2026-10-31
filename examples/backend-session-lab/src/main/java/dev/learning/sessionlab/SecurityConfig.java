package dev.learning.sessionlab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import java.util.Map;

@Configuration
class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService demoUsers(PasswordEncoder encoder) {
        // PUBLIC DEMO PASSWORDS. Never use these accounts or this setup in production.
        return new InMemoryUserDetailsManager(
            User.withUsername("alice").password(encoder.encode("alice-local-demo-17")).roles("USER").build(),
            User.withUsername("bob").password(encoder.encode("bob-local-demo-17")).roles("USER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityJson json) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .httpBasic(basic -> basic.disable())
            .cors(cors -> cors.disable()) // Same-origin local exercise; no cross-origin allowlist.
            .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    json.error(response, 401, "AUTHENTICATION_REQUIRED", "请先登录"))
                .accessDeniedHandler((request, response, exception) -> {
                    if (exception instanceof CsrfException) {
                        json.error(response, 403, "CSRF_REJECTED", "CSRF 校验失败，请重新获取令牌");
                    } else {
                        json.error(response, 403, "FORBIDDEN", "拒绝访问");
                    }
                }))
            .formLogin(form -> form
                // A custom page URL disables Spring's generated HTML login page.
                // Only POST is a login action; no HTML page is supplied by this exercise.
                .loginPage("/api/auth/login")
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) ->
                    json.write(response, 200, Map.of("username", authentication.getName())))
                .failureHandler((request, response, exception) ->
                    json.error(response, 401, "BAD_CREDENTIALS", "用户名或密码错误")))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }
}
