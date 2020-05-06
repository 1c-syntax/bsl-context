package com.github._1c_syntax.bsl.context.component;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class Type {
  String name;
  String nameEn;
  boolean isPrimitive;
  int descriptionId;
  Map<String, Method> methods;
  Map<String, Variable> variables;
}
