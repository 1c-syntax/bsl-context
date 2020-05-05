package com.github._1c_syntax.bsl.context.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public abstract class AbstractMethod {
  String name;
  String nameEn;
  boolean function;
  int descriptionId;
  List<SignatureMethod> signatures;
}
