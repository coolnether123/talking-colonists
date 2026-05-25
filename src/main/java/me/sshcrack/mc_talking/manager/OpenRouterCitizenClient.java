package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;
import me.sshcrack.mc_talking.manager.provider.ChatMessage;
import me.sshcrack.mc_talking.manager.provider.OpenAiSpeechToTextProvider;
import me.sshcrack.mc_talking.manager.provider.OpenAiTextToSpeechProvider;
import me.sshcrack.mc_talking.manager.provider.OpenRouterTextGenerationProvider;
import me.sshcrack.mc_talking.manager.provider.PcmAudioUtil;
import me.sshcrack.mc_talking.manager.provider.SpeechToTextProvider;
import me.sshcrack.mc_talking.manager.provider.TextGenerationProvider;
import me.sshcrack.mc_talking.manager.provider.TextToSpeechProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class OpenRouterCitizenClient implements ConversationClient {
    private static final int INPUT_SAMPLE_RATE = McTalkingVoicechatPlugin.TARGET_SAMPLE_RATE;
    private static final int MIN_UTTERANCE_SAMPLES = INPUT_SAMPLE_RATE / 4;

    private final AbstractEntityCitizen entity;
    private final OpusDecoder decoder;
    private final GeminiStream stream;
    private final TextGenerationProvider textGenerationProvider;
    private final SpeechToTextProvider speechToTextProvider;
    private final TextToSpeechProvider textToSpeechProvider;
    private final ExecutorService executor;
    private final List<ChatMessage> conversation = new ArrayList<>();
    private final List<short[]> speechBuffer = new ArrayList<>();
    private final List<Runnable> onCloseActions = new ArrayList<>();

    @Nullable
    private ServerPlayer player;
    @Nullable
    private Consumer<OpenRouterCitizenClient> onSystemConversationEnded;
    @Nullable
    private VisibleCitizenStatus lastStatus;

    private final boolean startedInSystemMode;
    private boolean closed;
    private boolean processing;
    private boolean shouldEndConversation;
    private boolean playerInputStarted;

    public OpenRouterCitizenClient(AbstractEntityCitizen entity, @Nullable Consumer<OpenRouterCitizenClient> onSystemConversationEnded) {
        this(new CitzienEntityAudioProvider(entity, null), entity, null, true, onSystemConversationEnded);
    }

    public OpenRouterCitizenClient(AudioProvider audioProvider, AbstractEntityCitizen entity, @Nullable ServerPlayer player) {
        this(audioProvider, entity, player, false, null);
    }

    private OpenRouterCitizenClient(AudioProvider audioProvider, AbstractEntityCitizen entity, @Nullable ServerPlayer player,
                                    boolean startedInSystemMode, @Nullable Consumer<OpenRouterCitizenClient> onSystemConversationEnded) {
        this.entity = entity;
        this.player = player;
        this.startedInSystemMode = startedInSystemMode;
        this.onSystemConversationEnded = onSystemConversationEnded;

        AudioChannel channel = audioProvider.createChannel();
        this.decoder = audioProvider.createDecoder();
        this.stream = new GeminiStream(channel);
        this.stream.setOnPause(() -> AiStatusHelper.setAiStatusSynced(entity, AiStatus.LISTENING));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.textGenerationProvider = new OpenRouterTextGenerationProvider(httpClient);
        this.speechToTextProvider = new OpenAiSpeechToTextProvider(httpClient);
        this.textToSpeechProvider = new OpenAiTextToSpeechProvider(httpClient);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mc-talking-openrouter-" + entity.getUUID());
            t.setDaemon(true);
            return t;
        });

        conversation.add(new ChatMessage("system", getSystemPrompt()));
    }

    public boolean isMumbling() {
        return player == null;
    }

    public void transitionToPlayer(ServerPlayer player) {
        this.player = player;
        this.onSystemConversationEnded = null;
        this.playerInputStarted = false;
    }

    @Override
    public void promptAudioOpus(byte[] audio) {
        if (decoder == null || closed) return;
        addPromptAudio(decoder.decode(audio));
    }

    @Override
    public void addPromptAudio(short[] audio) {
        if (closed || audio == null || audio.length == 0) return;
        synchronized (speechBuffer) {
            speechBuffer.add(audio);
        }
    }

    @Override
    public void addPromptTextAfterTalkingComplete(String text) {
        if (closed || text == null || text.isBlank()) return;
        if (processing) {
            conversation.add(new ChatMessage("system", text));
        } else {
            queueTextTurn(text, false);
        }
    }

    @Override
    public void addPromptTextImmediate(String text) {
        queueTextTurn(text, false);
    }

    @Override
    public void onAudioInputStopped() {
        if (closed || processing) return;

        short[] utterance;
        synchronized (speechBuffer) {
            utterance = flatten(speechBuffer);
            speechBuffer.clear();
        }

        if (utterance.length < MIN_UTTERANCE_SAMPLES) return;
        queueAudioTurn(utterance);
    }

    @Override
    public boolean requiresSilencePadding() {
        return false;
    }

    @Override
    public void endConversationWhenPossible() {
        shouldEndConversation = true;
        if (!processing) {
            closeConversation();
        }
    }

    @Override
    public void addOnCloseAction(Runnable action) {
        onCloseActions.add(action);
    }

    @Override
    public boolean sendStatusUpdates() {
        return true;
    }

    @Override
    @Nullable
    public VisibleCitizenStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(@Nullable VisibleCitizenStatus lastStatus) {
        this.lastStatus = lastStatus;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stream.close();
        executor.shutdownNow();
        AiStatusHelper.setAiStatusSynced(entity, AiStatus.NONE);

        for (Runnable action : onCloseActions) {
            try {
                action.run();
            } catch (Exception e) {
                McTalking.LOGGER.error("Error executing OpenRouter close action", e);
            }
        }
    }

    private void queueAudioTurn(short[] utterance) {
        processing = true;
        executor.submit(() -> {
            try {
                AiStatusHelper.setAiStatusSynced(entity, AiStatus.THINKING);
                maybeInjectPlayerTransition();
                String transcript = speechToTextProvider.transcribe(utterance, INPUT_SAMPLE_RATE);
                if (transcript.isBlank()) {
                    AiStatusHelper.setAiStatusSynced(entity, AiStatus.LISTENING);
                    return;
                }
                runTextTurn(transcript, true);
            } catch (Exception e) {
                onError(e);
            } finally {
                processing = false;
                if (shouldEndConversation) closeConversation();
            }
        });
    }

    private void queueTextTurn(String text, boolean userRole) {
        if (closed || text == null || text.isBlank()) return;
        processing = true;
        executor.submit(() -> {
            try {
                AiStatusHelper.setAiStatusSynced(entity, AiStatus.THINKING);
                runTextTurn(text, userRole);
            } catch (Exception e) {
                onError(e);
            } finally {
                processing = false;
                if (onSystemConversationEnded != null) {
                    onSystemConversationEnded.accept(this);
                }
                if (shouldEndConversation) closeConversation();
            }
        });
    }

    private void runTextTurn(String input, boolean userRole) throws Exception {
        conversation.add(new ChatMessage(userRole ? "user" : "system", input));
        String reply = textGenerationProvider.generate(conversation);
        if (reply.isBlank()) {
            AiStatusHelper.setAiStatusSynced(entity, AiStatus.LISTENING);
            return;
        }

        conversation.add(new ChatMessage("assistant", reply));
        sendTranscriptToChat(reply);

        if (McTalkingConfig.INSTANCE.instance().modality != ModalityModes.TEXT) {
            AiStatusHelper.setAiStatusSynced(entity, AiStatus.TALKING);
            short[] audio = textToSpeechProvider.synthesize(reply, INPUT_SAMPLE_RATE);
            stream.addGeminiPcmWithPitch(PcmAudioUtil.toLittleEndianBytes(audio), INPUT_SAMPLE_RATE);
            stream.flushAudio();
        } else {
            AiStatusHelper.setAiStatusSynced(entity, AiStatus.LISTENING);
        }
    }

    private void maybeInjectPlayerTransition() {
        if (!startedInSystemMode || player == null || playerInputStarted) return;
        String citizenName = entity.getDisplayName().getString();
        String playerName = player.getName().getString();
        conversation.add(new ChatMessage("system", String.format(
                "A real player named %s is now speaking to you directly in the game world. " +
                        "Respond naturally as %s speaking face to face with this person.",
                playerName, citizenName)));
        playerInputStarted = true;
    }

    private String getSystemPrompt() {
        if (startedInSystemMode) {
            var view = CitizenPromptViewFactory.create(entity.getCitizenData(), new HashMap<>(), null);
            return CitizenPromptService.generateSystemControlledRoleplayPrompt(view);
        }

        HashMap<UUID, String> interestedParties = new HashMap<>();
        if (player != null) {
            interestedParties.put(player.getUUID(), player.getName().getString());
        }

        var promptView = CitizenPromptViewFactory.create(entity.getCitizenData(), interestedParties, player);
        return CitizenPromptService.generateCitizenRoleplayPrompt(promptView);
    }

    private void sendTranscriptToChat(String reply) {
        var modality = McTalkingConfig.INSTANCE.instance().modality;
        var hasTextEnabled = modality == ModalityModes.TEXT || modality == ModalityModes.TEXT_AND_AUDIO;
        if (!hasTextEnabled) return;

        var message = entity.getDisplayName().copy().append(": ").append(Component.literal(reply));
        if (player != null) {
            player.sendSystemMessage(message);
            return;
        }

        if (!McTalkingConfig.INSTANCE.instance().sendMumblingAndConversationsToChat) return;
        var server = entity.level().getServer();
        if (server == null) return;
        double range = McTalkingConfig.INSTANCE.instance().mumblingDetectionRange * 2;
        for (ServerPlayer nearby : server.getPlayerList().getPlayers()) {
            if (nearby.level() == entity.level() && nearby.distanceTo(entity) <= range) {
                nearby.sendSystemMessage(message);
            }
        }
    }

    private void onError(Exception ex) {
        McTalking.LOGGER.error("Error in OpenRouter/OpenAI conversation backend", ex);
        if (player != null) {
            Objects.requireNonNull(player.getServer()).execute(() -> {
                AiStatusHelper.setAiStatusOnServerThread(entity, AiStatus.ERROR);
                if (player.hasPermissions(4) && Boolean.TRUE.equals(McTalkingConfig.INSTANCE.instance().sendErrorsToPlayers)) {
                    player.sendSystemMessage(Component.literal("OpenRouter/OpenAI backend error: " + ex.getMessage()));
                }
            });
        } else {
            AiStatusHelper.setAiStatusOnServerThread(entity, AiStatus.ERROR);
        }
    }

    private void closeConversation() {
        if (player != null) {
            ConversationManager.endConversation(player.getUUID(), false);
        } else {
            close();
        }
    }

    private static short[] flatten(List<short[]> chunks) {
        int total = chunks.stream().mapToInt(a -> a.length).sum();
        short[] out = new short[total];
        int offset = 0;
        for (short[] chunk : chunks) {
            System.arraycopy(chunk, 0, out, offset, chunk.length);
            offset += chunk.length;
        }
        return out;
    }
}
