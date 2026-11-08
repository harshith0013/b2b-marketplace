package com.b2bmarket.ai.internal;

import com.b2bmarket.ai.LabelReading;
import com.b2bmarket.ai.internal.ChatCompletionsClient.Answer;
import tools.jackson.databind.json.JsonMapper;

/** The two steps of reading a label: ask the provider, then read its answer. */
public final class LabelCalls {

  private LabelCalls() {}

  public static Answer ask(ChatCompletionsClient client, byte[] image, String contentType) {
    return client.completeWithImage(
        LabelPrompt.INSTRUCTIONS, LabelPrompt.ASK, image, contentType, LabelPrompt.schema());
  }

  public static LabelReading read(String content, JsonMapper json, String model) {
    return LabelPrompt.read(content, json, model);
  }
}
