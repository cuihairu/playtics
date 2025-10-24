package io.playtics.gateway.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.bind.Binder;
import java.util.Map;

@Component
public class AuthService {
    private final Map<String, String> apiKeySecrets;

    public AuthService(Environment env) {
        this.apiKeySecrets = Binder.get(env)
                .bind("playtics.auth.keys", Map.class)
                .orElse(Map.of());
    }

    public String getSecret(String apiKey) {
        return apiKeySecrets.get(apiKey);
    }
}
