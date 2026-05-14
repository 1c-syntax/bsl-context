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

  protected void processRawTypes(List<Context> contexts) {

    types.addAll(
            rawTypes.stream()
                    .map(t -> contexts.stream()
                            .filter(c -> c instanceof ContextType || c instanceof ContextEnum)
                            .filter(c -> c.name().getName().equals(t))
                            .findAny())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList()
    );

  }

}
