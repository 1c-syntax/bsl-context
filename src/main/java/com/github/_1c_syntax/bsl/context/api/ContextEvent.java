package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Контекстное событие.
 */
public interface ContextEvent {

  /**
   * Имя события.
   */
  ContextName name();

  /**
   * Сигнатуры события.
   */
  List<ContextMethodSignature> signatures();

  /**
   * Описание события.
   */
  String description();

  /**
   * Доступность события.
   */
  List<Availability> availabilities();

}
