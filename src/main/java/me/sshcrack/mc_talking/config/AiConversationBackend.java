package me.sshcrack.mc_talking.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum AiConversationBackend implements NameableEnum {
    GEMINI_LIVE,
    OPENROUTER_OPENAI_AUDIO;

    @Override
    public Component getDisplayName() {
        return Component.literal(name());
    }
}
