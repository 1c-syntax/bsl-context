package com.github._1c_syntax.bsl.context.component;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Variable {
  String name;
  String nameEn;
  int descriptionId;
}
