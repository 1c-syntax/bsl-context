package com.github._1c_syntax.bsl.context.platform;

import java.util.Map;
import com.github._1c_syntax.bsl.context.api.*;
import lombok.Builder;

import java.util.List;

@Builder
public class PlatformContextEvent implements ContextEvent{

  private final ContextName name;
  private final List<ContextMethodSignature> signatures;
  private final String description;
  private final List<Availability> availabilities;
  @Builder.Default
  private final String sinceVersion = "";
  @Builder.Default
  private final String deprecatedSinceVersion = "";
  @Builder.Default
  private final List<String> recommendedReplacements = List.of();
  @Builder.Default
  private final String notes = "";
  @Builder.Default
  private final List<String> examples = List.of();
  @Builder.Default
  private final List<String> seeAlso = List.of();

  @Override
  public ContextName name() {
    return name;
  }

  @Override
  public String notes() {
    return notes;
  }

  @Override
  public List<String> examples() {
    return List.copyOf(examples);
  }

  @Override
  public List<String> seeAlso() {
    return List.copyOf(seeAlso);
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

  protected void processRawTypes(Map<String, Context> typeIndex) {
    for (var sig : signatures) {
      ((PlatformContextMethodSignature) sig).processRawTypes(typeIndex);
    }
  }

}
