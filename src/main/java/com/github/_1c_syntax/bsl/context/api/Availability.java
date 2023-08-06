package com.github._1c_syntax.bsl.context.api;

import java.util.Arrays;
import java.util.Optional;

/**
 * Доступность свойства, метода, события, по виду клиента.
 */
public enum Availability {
  THIN_CLIENT("Тонкий клиент", "Thin client"),
  WEB_CLIENT("Веб-клиент", "Web-client"),
  MOBILE_CLIENT("Мобильный клиент", "Mobile client"),
  SERVER("Сервер", "Server"),
  THICK_CLIENT("Толстый клиент", "Thick client"),
  EXTERNAL_CONNECTION("Внешнее соединение", "External connection"),
  MOBILE_APPLICATION_CLIENT("Мобильное приложение (клиент)", "Mobile application (client)"),
  MOBILE_APPLICATION_SERVER("Мобильное приложение (сервер)", "Mobile application (server)"),
  MOBILE_STANDALONE_SERVER("Мобильный автономный сервер", "Mobile standalone server")
  ;

  private final ContextName name;

  Availability(String name, String alias) {
    this.name = new ContextName(name, alias);
  }

  public static Optional<Availability> findByName(String name) {
    return Arrays.stream(Availability.values())
            .filter(value -> value.name.getName().equalsIgnoreCase(name)
                    || value.name.getAlias().equalsIgnoreCase(name))
            .findFirst();
  }

  @Override
  public String toString() {
    return name.toString();
  }

}
