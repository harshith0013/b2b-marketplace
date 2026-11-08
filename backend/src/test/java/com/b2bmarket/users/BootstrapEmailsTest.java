package com.b2bmarket.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BootstrapEmailsTest {

  @Test
  void parsesACommaSeparatedListCaseInsensitively() {
    assertThat(UserService.parseEmails(" Asha@Example.com, e2e-reviewer@example.test ,, "))
        .containsExactlyInAnyOrder("asha@example.com", "e2e-reviewer@example.test");
  }

  @Test
  void blankMeansNobody() {
    assertThat(UserService.parseEmails("")).isEmpty();
    assertThat(UserService.parseEmails("  ,  ")).isEmpty();
  }
}
