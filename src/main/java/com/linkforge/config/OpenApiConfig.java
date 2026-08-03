package com.linkforge.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger UI configuration.
 * Available at: http://localhost:8080/swagger-ui.html
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "LinkForge API",
        version = "1.0.0",
        description = """
            Secure, scalable, analytics-driven URL management platform.
            
            ## Authentication
            Most endpoints require a Bearer JWT token. Obtain it via `POST /api/v1/auth/login`.
            
            ## Rate Limiting
            - Authenticated users: 60 req/min
            - Redirect endpoint: 300 req/min
            - API keys: 100 req/min (configurable per key)
            """,
        contact = @Contact(name = "LinkForge", email = "api@linkforge.io"),
        license = @License(name = "MIT", url = "https://opensource.org/licenses/MIT")
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local Development"),
        @Server(url = "https://api.linkforge.io", description = "Production")
    }
)
@SecurityScheme(
    name = "Bearer Authentication",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
public class OpenApiConfig {
}
