package com.github._1c_syntax.bsl.context.context;

import com.github._1c_syntax.bsl.context.entity.AbstractMethod;
import com.github._1c_syntax.bsl.context.entity.AbstractType;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public interface BSLContext {

  void initialize();

  Map<String, AbstractMethod> getMethods(URI uri);

  Optional<AbstractMethod> getMethod(URI uri, String methodName);

  Map<String, AbstractType> getTypes(URI uri);

  Optional<AbstractType> getType(URI uri, String typeName);

  void shutdown();

}
