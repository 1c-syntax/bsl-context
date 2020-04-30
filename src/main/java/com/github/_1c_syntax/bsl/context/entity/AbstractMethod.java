package com.github._1c_syntax.bsl.context.entity;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class AbstractMethod {
  String name;
  String nameEn;
  boolean function;
  List<MethodParameter> parameters;
  List<AbstractType> returnValues;
  int descriptionId;
}
