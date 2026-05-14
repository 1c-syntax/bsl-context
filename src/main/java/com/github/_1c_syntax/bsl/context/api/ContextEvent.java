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

  /**
   * Версия платформы, начиная с которой доступно событие.
   */
  default String sinceVersion() {
    return "";
  }

  /**
   * Версия платформы, начиная с которой событие помечено как не рекомендуемое.
   */
  default String deprecatedSinceVersion() {
    return "";
  }

  /**
   * Признак generic-события (имя содержит {@code <…>}-плейсхолдер).
   */
  default boolean isGeneric() {
    return ContextNames.isGeneric(name());
  }

  /**
   * Имена, рекомендованные в качестве замены устаревшего события.
   */
  default List<String> recommendedReplacements() {
    return List.of();
  }

}
