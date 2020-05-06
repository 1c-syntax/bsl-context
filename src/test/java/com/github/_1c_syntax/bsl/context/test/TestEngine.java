package com.github._1c_syntax.bsl.context.test;

import com.github._1c_syntax.bsl.context.BSLEngine;
import com.github._1c_syntax.bsl.context.component.Method;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestEngine implements BSLEngine {

  Map<String, Method> globalMethods = new CaseInsensitiveMap<>();

  public TestEngine() {
    fillGlobalMethod();
  }

  @Override
  public Map<String, Method> getGlobalMethods() {
    return globalMethods;
  }

  private void fillGlobalMethod() {

    List<String> keys = new ArrayList<>();
    keys.add("Сообщить");
    keys.add("СоздатьФайбрикуXDTO");

    keys.stream()
      .map(name -> Method.builder()
        .name(name)
        .nameEn(name)
        .build())
      .forEach(method -> globalMethods.put(method.getName(), method));

  }
}
