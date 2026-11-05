package com.peb.service.planning;

import com.peb.controller.planning.PlanningController;
import jakarta.servlet.Filter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan("com.peb.model.planning")
@EnableJpaRepositories("com.peb.repository.planning")
@Import({PlanningController.class, PlanningProjectService.class, PlanningExcelService.class, PlanningValidationService.class})
public class PlanningPreviewApplication {
    public static void main(String[] arguments) {
        SpringApplication application = new SpringApplication(PlanningPreviewApplication.class);
        String port = java.util.Arrays.stream(arguments).filter(argument -> argument.startsWith("--server.port="))
            .findFirst().orElse("--server.port=8181");
        String[] defaults = {"--server.address=127.0.0.1", port, "--server.ssl.enabled=false",
                "--server.servlet.session.cookie.secure=false", "--spring.datasource.url=jdbc:h2:mem:planning_preview;MODE=Oracle;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "--spring.jpa.hibernate.ddl-auto=create-drop",
            "--spring.thymeleaf.cache=false", "--spring.jmx.enabled=false"};
        application.run(defaults);
    }

    @Bean
    SecurityFilterChain previewSecurity(HttpSecurity http) throws Exception {
        Filter identity = (request, response, chain) -> {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("local-preview", "unused", "READ", "WRITE", "DELETE", "EXECUTE"));
            try { chain.doFilter(request, response); }
            finally { SecurityContextHolder.clearContext(); }
        };
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterAfter(identity, SecurityContextHolderFilter.class);
        return http.build();
    }
}