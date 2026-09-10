package com.module.paymentsms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/intasend/transaction/webhook",
                        "/api/v1/intasend/transaction/collection-webhook",
                        "/api/v1/intasend/transaction/send-money-webhook"
                ).permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // Spring's DefaultHandlerExceptionResolver resolves MVC exceptions (e.g. a bad
                // enum value in a @RequestBody) via response.sendError(...), which Tomcat handles
                // by internally forwarding to /error. Without this permit, that forward hits
                // .anyRequest().authenticated() below and gets rejected as unauthenticated - so a
                // request that should have gotten a clean 400 instead silently got an empty 403.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
