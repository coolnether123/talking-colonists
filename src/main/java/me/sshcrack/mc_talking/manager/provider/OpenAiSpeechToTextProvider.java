package me.sshcrack.mc_talking.manager.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.SpeechToTextBackend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OpenAiSpeechToTextProvider implements SpeechToTextProvider {
    private static final URI TRANSCRIPTIONS = URI.create("https://api.openai.com/v1/audio/transcriptions");

    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public OpenAiSpeechToTextProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String transcribe(short[] pcm, int sampleRate) throws AiProviderException {
        var config = McTalkingConfig.INSTANCE.instance();
        if (config.openAiApiKey.isBlank()) {
            throw new AiProviderException("OpenAI API key is empty");
        }
        if (pcm.length == 0) {
            return "";
        }

        String boundary = "----mctalking-" + UUID.randomUUID();
        byte[] wav = PcmAudioUtil.toWav(pcm, sampleRate);
        String model = config.speechToTextBackend == SpeechToTextBackend.OPENAI_GPT4O_MINI_TRANSCRIBE
                ? "gpt-4o-mini-transcribe"
                : "whisper-1";

        HttpRequest request = HttpRequest.newBuilder(TRANSCRIPTIONS)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.openAiApiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(ofMimeMultipartData(boundary, List.of(
                        Part.text("model", model),
                        Part.text("response_format", "json"),
                        Part.file("file", "speech.wav", "audio/wav", wav)
                )))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AiProviderException("OpenAI transcription failed: HTTP " + response.statusCode() + " " + response.body());
            }

            JsonObject root = gson.fromJson(response.body(), JsonObject.class);
            if (root == null || !root.has("text")) {
                throw new AiProviderException("OpenAI transcription returned no text");
            }

            return root.get("text").getAsString().trim();
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("OpenAI transcription request failed", e);
        }
    }

    private static HttpRequest.BodyPublisher ofMimeMultipartData(String boundary, List<Part> parts) {
        List<byte[]> byteArrays = new ArrayList<>();
        for (Part part : parts) {
            byteArrays.add(("--" + boundary + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byteArrays.add(part.header().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byteArrays.add("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byteArrays.add(part.content());
            byteArrays.add("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        byteArrays.add(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }

    private record Part(String header, byte[] content) {
        static Part text(String name, String value) {
            return new Part("Content-Disposition: form-data; name=\"" + name + "\"\r\n", value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        static Part file(String name, String filename, String contentType, byte[] value) {
            return new Part(
                    "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n" +
                            "Content-Type: " + contentType + "\r\n",
                    value);
        }
    }
}
