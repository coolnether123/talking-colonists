package me.sshcrack.mc_talking.manager.provider;

public interface TextToSpeechProvider {
    short[] synthesize(String text, int targetSampleRate) throws AiProviderException;
}
