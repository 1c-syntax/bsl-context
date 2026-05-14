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
  public String toString() {
    return name.toString();
  }

  protected void processRawTypes(List<Context> contexts) {

    signatures.forEach(contextMethodSignature -> ((PlatformContextMethodSignature) contextMethodSignature).processRawTypes(contexts));

  }

}
