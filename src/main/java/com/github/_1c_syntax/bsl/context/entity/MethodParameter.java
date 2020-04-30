package com.github._1c_syntax.bsl.context.entity;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class MethodParameter {
  String name;
  String nameEn;
  AbstractMethod owner; // на подумать
  int position;
  boolean required;
  int descriptionId;
}
