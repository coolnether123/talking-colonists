package me.sshcrack.mc_talking.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum SpeechToTextBackend implements NameableEnum {
    OPENAI_WHISPER,
    OPENAI_GPT4O_MINI_TRANSCRIBE;

    @Override
    public Component getDisplayName() {
        return Component.literal(name());
    }
}
