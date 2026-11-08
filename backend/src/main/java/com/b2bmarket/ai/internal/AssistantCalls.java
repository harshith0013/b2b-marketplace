package com.b2bmarket.ai.internal;

import com.b2bmarket.ai.AssistantDtos.CategoryChoice;
import com.b2bmarket.ai.AssistantDtos.ProposedFilters;
import com.b2bmarket.ai.internal.ChatCompletionsClient.Answer;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** The two steps of a question: ask the provider, then read its answer. */
public final class AssistantCalls {

  private AssistantCalls() {}

  public static Answer ask(
      ChatCompletionsClient client, List<CategoryChoice> categories, String question) {
    return client.complete(
        AssistantPrompt.instructions(categories), question, AssistantPrompt.schema(categories));
  }

  public static ProposedFilters read(String content, JsonMapper json) {
    return AssistantPrompt.read(content, json);
  }
}
