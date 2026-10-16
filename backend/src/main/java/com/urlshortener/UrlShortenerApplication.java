package com.urlshortener;

import com.urlshortener.service.ApiKeyProvisioningService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import com.urlshortener.config.RateLimitConfig;

@SpringBootApplication
@EnableConfigurationProperties(RateLimitConfig.class)
public class UrlShortenerApplication {

    public static void main(String[] args) {
        if (containsKeyCreationCommand(args)) {
            createApiKey(args);
            return;
        }
        SpringApplication.run(UrlShortenerApplication.class, args);
    }

    private static boolean containsKeyCreationCommand(String[] args) {
        for (String arg : args) {
            if ("--create-api-key".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void createApiKey(String[] args) {
        SpringApplication application = new SpringApplication(UrlShortenerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            String apiKey = context.getBean(ApiKeyProvisioningService.class).createApiKey();
            System.out.println("API_KEY=" + apiKey);
        }
    }
}
