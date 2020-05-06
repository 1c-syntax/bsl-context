package com.github._1c_syntax.bsl.context.component;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MethodParameter {
  String name;
  String nameEn;
  int position;
  boolean required;
  int descriptionId;
  List<Type> types;
}
