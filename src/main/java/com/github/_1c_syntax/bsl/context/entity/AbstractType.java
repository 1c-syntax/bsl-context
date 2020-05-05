package com.github._1c_syntax.bsl.context.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public abstract class AbstractType {
  String name;
  String nameEn;
  boolean isPrimitive;
  int descriptionId;
  Map<String, AbstractMethod> methods;
  Map<String, AbstractVariable> variables;
}
