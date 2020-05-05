package com.github._1c_syntax.bsl.context.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public abstract class MethodParameter {
  String name;
  String nameEn;
  int position;
  boolean required;
  int descriptionId;
  List<AbstractType> types;
}
