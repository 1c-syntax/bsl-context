package com.github._1c_syntax.bsl.context.entity;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AbstractVariable {
  String name;
  String nameEn;
  int descriptionId;
}
