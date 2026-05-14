package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.*;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Builder
public class PlatformContextSignatureParameter implements ContextSignatureParameter {

  private final ContextName name;
  private final boolean isRequired;
  private final List<Context> types = new ArrayList<>();
  private final String description;
  @lombok.Builder.Default
  private final String defaultValue = "";

  private final List<String> rawTypes;

  @Override
  public ContextName name() {
    return name;
  }

  @Override
  public boolean isRequired() {
    return isRequired;
  }

  @Override
  public List<Context> types() {
    return List.copyOf(types);
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public String defaultValue() {
    return defaultValue;
  }

  protected void processRawTypes(java.util.Map<String, Context> typeIndex) {
    for (var raw : rawTypes) {
      var resolved = typeIndex.get(raw);
      if (resolved != null) {
        types.add(resolved);
      }
    }
  }

}
