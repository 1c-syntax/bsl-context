package com.github._1c_syntax.bsl.context.component;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SignatureMethod {
  String syntax;
  String syntaxEn;
  int descriptionId;
  List<MethodParameter> parameters;
}
