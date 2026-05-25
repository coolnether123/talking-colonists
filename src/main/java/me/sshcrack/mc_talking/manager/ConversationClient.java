package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import org.jetbrains.annotations.Nullable;

public interface ConversationClient extends AutoCloseable {
    void promptAudioOpus(byte[] audio);

    void addPromptAudio(short[] audio);

    void addPromptTextAfterTalkingComplete(String text);

    void addPromptTextImmediate(String text);

    void onAudioInputStopped();

    boolean requiresSilencePadding();

    void endConversationWhenPossible();

    void addOnCloseAction(Runnable action);

    boolean sendStatusUpdates();

    @Nullable
    VisibleCitizenStatus getLastStatus();

    void setLastStatus(@Nullable VisibleCitizenStatus lastStatus);

    @Override
    void close();
}
