package com.eaortiz.producer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // Relative server so "Try it out" uses same scheme as the page (https behind Railway)
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("Producer API")
                        .description("Device updates and registry API. Click 'Authorize' (top right), enter username 'admin' and password 'admin', then click Authorize. "
                                + "If you skip this, Execute will trigger a browser login prompt.")
                        .version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
                .components(new Components()
                        .addSecuritySchemes("basicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                                        .description("HTTP Basic (e.g. admin / admin)")));
    }
}
