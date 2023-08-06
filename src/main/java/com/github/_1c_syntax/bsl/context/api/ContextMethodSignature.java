package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Сигнатура контекстного метода.
 */
public interface ContextMethodSignature {

  /**
   * Имя сигнатуры метода
   */
  ContextName name();

  /**
   * Параметры сигнатуры метода
   */
  List<ContextSignatureParameter> parameters();

  /**
   * Описание сигнатуры метода
   */
  String description();
}
