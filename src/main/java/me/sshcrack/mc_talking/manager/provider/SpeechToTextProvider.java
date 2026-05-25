package me.sshcrack.mc_talking.manager.provider;

public interface SpeechToTextProvider {
    String transcribe(short[] pcm, int sampleRate) throws AiProviderException;
}
