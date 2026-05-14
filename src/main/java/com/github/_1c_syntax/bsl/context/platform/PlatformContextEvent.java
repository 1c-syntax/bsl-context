package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.*;
import lombok.Builder;

import java.util.List;

@Builder
public class PlatformContextEvent implements ContextEvent{

  private final ContextName name;
  private final List<ContextMethodSignature> signatures;
  private final String description;
  private final List<Availability> availabilities;
  @lombok.Builder.Default
  private final String sinceVersion = "";
  @lombok.Builder.Default
  private final String deprecatedSinceVersion = "";
  @lombok.Builder.Default
  private final List<String> recommendedReplacements = List.of();

  @Override
  public ContextName name() {
    return name;
  }

  @Override
  public List<ContextMethodSignature> signatures() {
    return List.copyOf(signatures);
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public List<Availability> availabilities() {
    return List.copyOf(availabilities);
  }

  @Override
  public String sinceVersion() {
    return sinceVersion;
  }

  @Override
  public String deprecatedSinceVersion() {
    return deprecatedSinceVersion;
  }

  @Override
  public List<String> recommendedReplacements() {
    return List.copyOf(recommendedReplacements);
  }

  @Override
  public String toString() {
    return name.toString();
  }

  protected void processRawTypes(java.util.Map<String, Context> typeIndex) {
    for (var sig : signatures) {
      ((PlatformContextMethodSignature) sig).processRawTypes(typeIndex);
    }
  }

}
