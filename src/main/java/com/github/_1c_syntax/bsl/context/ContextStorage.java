package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.component.Method;
import lombok.Getter;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContextStorage {

  List<BSLEngine> engines = new ArrayList<>();
  @Getter
  Map<String, Method> globalMethods = new CaseInsensitiveMap<>();

  public void registerEngine(BSLEngine engine) {
    engines.add(engine);
  }

  public void process() {
    engines.forEach(this::processEngine);
  }

  private void processEngine(BSLEngine engine) {
    engine.getGlobalMethods().forEach((key, value) -> globalMethods.put(key, value));
  }

  public void clear() {
    globalMethods.clear();
    engines.clear();
  }

}
