package com.b2bmarket.platform;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantScopeTest {

  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final TenantScope scope = new TenantScope(jdbc);

  @Test
  void refusesToRunOutsideATransactionWhereTheScopeWouldBeLost() {
    assertThatThrownBy(() -> scope.enter(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(jdbc);
  }

  @Test
  void requiresABusiness() {
    assertThatThrownBy(() -> scope.enter(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
