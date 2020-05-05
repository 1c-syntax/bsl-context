package com.github._1c_syntax.bsl.context.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SignatureMethod {
  String syntax;
  String syntaxEn;
  int descriptionId;
  List<MethodParameter> parameters;
}
