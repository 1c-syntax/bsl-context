package com.github._1c_syntax.bsl.context.component;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Method {
  String name;
  String nameEn;
  boolean function;
  int descriptionId;
  List<SignatureMethod> signatures;
}
