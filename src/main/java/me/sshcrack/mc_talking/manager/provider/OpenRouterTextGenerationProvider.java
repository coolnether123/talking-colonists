package me.sshcrack.mc_talking.manager.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenRouterTextGenerationProvider implements TextGenerationProvider {
    private static final URI CHAT_COMPLETIONS = URI.create("https://openrouter.ai/api/v1/chat/completions");

    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public OpenRouterTextGenerationProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String generate(List<ChatMessage> messages) throws AiProviderException {
        var config = McTalkingConfig.INSTANCE.instance();
        if (config.openRouterApiKey.isBlank()) {
            throw new AiProviderException("OpenRouter API key is empty");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", config.openRouterModel);
        body.put("messages", messages);
        body.put("temperature", config.openRouterTemperature);
        body.put("max_tokens", config.openRouterMaxTokens);

        HttpRequest request = HttpRequest.newBuilder(CHAT_COMPLETIONS)
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + config.openRouterApiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://github.com/coolnether123/talking-colonists")
                .header("X-Title", "MineColonies Talking Citizens USW")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AiProviderException("OpenRouter text generation failed: HTTP " + response.statusCode() + " " + response.body());
            }

            JsonObject root = gson.fromJson(response.body(), JsonObject.class);
            var choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiProviderException("OpenRouter returned no choices");
            }

            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                throw new AiProviderException("OpenRouter returned no message content");
            }

            return message.get("content").getAsString().trim();
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("OpenRouter text generation request failed", e);
        }
    }
}
