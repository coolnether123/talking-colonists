package me.sshcrack.mc_talking.manager.provider;

import com.google.gson.Gson;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.TextToSpeechBackend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class OpenAiTextToSpeechProvider implements TextToSpeechProvider {
    private static final URI SPEECH = URI.create("https://api.openai.com/v1/audio/speech");
    private static final int OPENAI_PCM_SAMPLE_RATE = 24000;

    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public OpenAiTextToSpeechProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public short[] synthesize(String text, int targetSampleRate) throws AiProviderException {
        var config = McTalkingConfig.INSTANCE.instance();
        if (config.openAiApiKey.isBlank()) {
            throw new AiProviderException("OpenAI API key is empty");
        }
        if (text.isBlank()) {
            return new short[0];
        }

        String model = config.textToSpeechBackend == TextToSpeechBackend.OPENAI_TTS_1 ? "tts-1" : "gpt-4o-mini-tts";
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("voice", config.openAiTtsVoice);
        body.put("input", text);
        body.put("response_format", "pcm");
        if (config.textToSpeechBackend == TextToSpeechBackend.OPENAI_GPT4O_MINI_TTS) {
            body.put("instructions", "Speak naturally as a Minecraft colony citizen. Keep it brief and conversational.");
        }

        HttpRequest request = HttpRequest.newBuilder(SPEECH)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.openAiApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new AiProviderException("OpenAI text-to-speech failed: HTTP " + response.statusCode() + " " +
                        new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
            }

            short[] samples = PcmAudioUtil.fromLittleEndianBytes(response.body());
            return PcmAudioUtil.resample(samples, OPENAI_PCM_SAMPLE_RATE, targetSampleRate);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("OpenAI text-to-speech request failed", e);
        }
    }
}
