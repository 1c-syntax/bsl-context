package com.github._1c_syntax.bsl.context.entity;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class AbstractType {
  String name;
  String nameEn;
  boolean isPrimitive;
  int descriptionId;
  Map<String, AbstractMethod> methods;
  Map<String, AbstractVariable> variables;
}
