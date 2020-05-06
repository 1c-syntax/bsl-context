package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.test.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextStorageTest {

  @Test
  void test_context_storage() {

    var countMethods = 2;

    BSLEngine engine = new TestEngine();
    assertThat(engine.getGlobalMethods()).hasSize(countMethods);

    ContextStorage storage = new ContextStorage();
    assertThat(storage.engines).isEmpty();

    storage.registerEngine(engine);
    assertThat(storage.engines).hasSize(1);

    storage.process();
    assertThat(storage.getGlobalMethods()).hasSize(countMethods);
    assertThat(Optional.of(storage.getGlobalMethods()).map(map -> map.get("сообщить"))).isPresent();
    assertThat(Optional.of(storage.getGlobalMethods()).map(map -> map.get("Удалить"))).isEmpty();

    storage.clear();
    assertThat(storage.getGlobalMethods()).isEmpty();
    assertThat(storage.engines).isEmpty();

  }

}
