package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

public class ArbitraryType extends PrimitiveType {
  private static final ContextName NAME = new ContextName("Произвольный", "Arbitrary");

  @Override
  public ContextName name() {
    return NAME;
  }

  @Override
  public String toString() {
    return NAME.toString();
  }
}
