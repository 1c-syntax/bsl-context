package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.component.Method;

import java.util.Map;

public interface BSLEngine {

  Map<String, Method> getGlobalMethods();

}
