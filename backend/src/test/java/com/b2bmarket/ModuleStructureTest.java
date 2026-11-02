package com.b2bmarket;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build when a module uses another module's internals (anything below its base package,
 * such as entities and repositories) or when modules depend on each other in a cycle.
 */
class ModuleStructureTest {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(B2bMarketplaceApplication.class).verify();
  }
}
