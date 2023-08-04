package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.*;
import lombok.Builder;

import java.util.List;

@Builder
public class PlatformGlobalContext implements Context {

  private final ContextName name = new ContextName("Глобальный контекст", "Global context");
  private final List<ContextMethod> methods;
  private final List<ContextEvent> externalConnectionModuleEvents;
  private final List<ContextEvent> sessionModuleEvents;
  private final List<ContextEvent> ordinaryApplicationEvents;
  private final List<ContextEvent> applicationEvents;
  private final List<ContextProperty> properties;

  @Override
  public ContextName name() { return name; }

  @Override
  public ContextKind kind() { return ContextKind.GLOBAL_CONTEXT; }

  public List<ContextMethod> methods() {
    return List.copyOf(methods);
  }

  public List<ContextEvent> externalConnectionModuleEvents() {
    return List.copyOf(externalConnectionModuleEvents);
  }

  public List<ContextEvent> sessionModuleEvents() {
    return List.copyOf(sessionModuleEvents);
  }

  public List<ContextEvent> ordinaryApplicationEvents() {
    return List.copyOf(ordinaryApplicationEvents);
  }

  public List<ContextEvent> applicationEvents() {
    return List.copyOf(applicationEvents);
  }

  public List<ContextProperty> properties() {
    return List.copyOf(properties);
  }
}
