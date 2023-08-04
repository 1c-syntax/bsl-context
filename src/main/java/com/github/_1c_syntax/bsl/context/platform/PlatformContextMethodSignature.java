package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import lombok.Builder;

import java.util.List;

@Builder
public class PlatformContextMethodSignature implements ContextMethodSignature {

  private final ContextName name;
  private final List<ContextSignatureParameter> parameters;
  private final String description;


  @Override
  public ContextName name() {
    return name;
  }

  @Override
  public List<ContextSignatureParameter> parameters() {
    return List.copyOf(parameters);
  }

  @Override
  public String description() {
    return description;
  }

  protected void processRawTypes(List<Context> contexts) {

    parameters.forEach(contextSignatureParameter -> ((PlatformContextSignatureParameter) contextSignatureParameter).processRawTypes(contexts));

  }

}
