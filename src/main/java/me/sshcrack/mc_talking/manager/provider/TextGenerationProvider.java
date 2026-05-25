package me.sshcrack.mc_talking.manager.provider;

import java.util.List;

public interface TextGenerationProvider {
    String generate(List<ChatMessage> messages) throws AiProviderException;
}
