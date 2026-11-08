package com.b2bmarket.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.b2bmarket.ai.AssistantDtos.CategoryChoice;
import com.b2bmarket.ai.AssistantDtos.ProposedFilters;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Measures the real assistant against the evaluation set (src/test/resources/ai-eval). Calls the
 * real provider, so it runs only on request: {@code mvn test -Pai-eval} with AI_API_KEY (or
 * GEMINI_API_KEY) set. Writes target/ai-eval-report.txt. Each field the set expects is one check; a
 * field expected to be null must come back null (for example, the prompt-injection question).
 */
@Tag("ai-eval")
class AiEvalTest {

  /** Lowest share of checks that must pass; raise it as the prompt improves. */
  static final double PASS_MARK = 0.8;

  final JsonMapper json = JsonMapper.builder().build();

  @Test
  void assistantUnderstandsTheEvaluationQuestions() throws IOException, InterruptedException {
    String key = firstNonBlank(System.getenv("AI_API_KEY"), System.getenv("GEMINI_API_KEY"));
    assumeTrue(key != null, "No AI_API_KEY or GEMINI_API_KEY: skipping the live evaluation");
    AiProperties properties =
        new AiProperties(
            env("AI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai"),
            key,
            env("AI_MODEL", "gemini-3.1-flash-lite"),
            env("AI_REASONING_EFFORT", "none"),
            Duration.ofSeconds(20),
            1000,
            1000,
            1000,
            90);
    ChatCompletionsClient client = new ChatCompletionsClient(properties);
    long pauseMillis = Long.parseLong(env("AI_EVAL_PAUSE_MS", "7000")); // free tier: ~10 a minute

    List<CategoryChoice> categories = new ArrayList<>();
    for (JsonNode c : json.readTree(resource("ai-eval/categories.json"))) {
      categories.add(new CategoryChoice(c.get("slug").asString(), c.get("name").asString()));
    }

    Map<String, int[]> byField = new TreeMap<>();
    Map<String, int[]> byLanguage = new TreeMap<>();
    List<String> misses = new ArrayList<>();
    int passed = 0;
    int total = 0;
    long totalMillis = 0;
    String[] lines = resource("ai-eval/queries.jsonl").split("\n");
    for (String line : lines) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode item = json.readTree(line);
      String question = item.get("q").asString();
      String language = item.get("lang").asString();
      long started = System.nanoTime();
      ProposedFilters got;
      try {
        got = AssistantCalls.read(AssistantCalls.ask(client, categories, question).content(), json);
      } catch (RuntimeException e) {
        got = new ProposedFilters(null, null, null, null, null, null, null, null, null, null);
        misses.add(question + " -> call failed: " + e.getMessage());
      }
      totalMillis += (System.nanoTime() - started) / 1_000_000;
      for (Map.Entry<String, JsonNode> expected : item.get("expect").properties()) {
        boolean ok = matches(expected.getKey(), expected.getValue(), got);
        count(byField, expected.getKey(), ok);
        count(byLanguage, language, ok);
        total++;
        if (ok) {
          passed++;
        } else {
          misses.add(
              question
                  + " -> "
                  + expected.getKey()
                  + ": expected "
                  + expected.getValue()
                  + ", got "
                  + got);
        }
      }
      Thread.sleep(pauseMillis);
    }

    double score = (double) passed / total;
    StringBuilder report = new StringBuilder();
    report.append("Model: ").append(properties.model()).append('\n');
    report.append(
        String.format(Locale.ROOT, "Overall: %d/%d checks = %.1f%%%n", passed, total, score * 100));
    report.append(
        String.format(
            Locale.ROOT, "Average answer time: %d ms%n", totalMillis / Math.max(1, lines.length)));
    report.append("\nBy field:\n");
    byField.forEach(
        (k, v) -> report.append(String.format(Locale.ROOT, "  %-24s %d/%d%n", k, v[0], v[1])));
    report.append("\nBy language:\n");
    byLanguage.forEach(
        (k, v) -> report.append(String.format(Locale.ROOT, "  %-8s %d/%d%n", k, v[0], v[1])));
    report.append("\nMisses:\n");
    misses.forEach(m -> report.append("  ").append(m).append('\n'));
    Path out = Path.of("target", "ai-eval-report.txt");
    Files.createDirectories(out.getParent());
    Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
    System.out.println(report);

    assertThat(score).as("share of evaluation checks passed").isGreaterThanOrEqualTo(PASS_MARK);
  }

  static boolean matches(String field, JsonNode expected, ProposedFilters got) {
    Object actual =
        switch (field) {
          case "keywords" -> got.keywords();
          case "category" -> got.category();
          case "city" -> got.city();
          case "state" -> got.state();
          case "quantity" -> got.quantity();
          case "unit" -> got.unit();
          case "maxPricePerPackRupees" -> got.maxPricePerPackRupees();
          case "maxOrderValueRupees" -> got.maxOrderValueRupees();
          case "minDaysLeft" -> got.minDaysLeft();
          case "sort" -> got.sort();
          default -> throw new IllegalArgumentException("Unknown field " + field);
        };
    if (expected.isNull()) {
      return actual == null;
    }
    if (actual == null) {
      return false;
    }
    if (expected.isNumber()) {
      return new BigDecimal(actual.toString()).compareTo(expected.decimalValue()) == 0;
    }
    String want = expected.asString().toLowerCase(Locale.ROOT);
    String have = actual.toString().toLowerCase(Locale.ROOT);
    // Keywords only need to contain the expected word; places and codes must match exactly.
    return "keywords".equals(field) ? have.contains(want) : have.equals(want);
  }

  private static void count(Map<String, int[]> counts, String key, boolean ok) {
    int[] c = counts.computeIfAbsent(key, k -> new int[2]);
    c[0] += ok ? 1 : 0;
    c[1]++;
  }

  private static String resource(String path) throws IOException {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
