package me.sshcrack.mc_talking.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum TextToSpeechBackend implements NameableEnum {
    OPENAI_GPT4O_MINI_TTS,
    OPENAI_TTS_1;

    @Override
    public Component getDisplayName() {
        return Component.literal(name());
    }
}
