package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.context.BSLContext;

public class ContextSupplier {

  BSLContext context;

  public void register(BSLContext context){
    this.context = context;
  }

  public void unregister() {
    context.shutdown();
    context = null; // todo: ??
  }

}
