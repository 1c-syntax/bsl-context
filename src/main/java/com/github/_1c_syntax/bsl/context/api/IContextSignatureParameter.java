package com.github._1c_syntax.bsl.context.api;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.IContext;

import java.util.List;

/**
 * Параметр контекстной сигнатуры метода.
 */
public interface IContextSignatureParameter {
    /**
     * Имя параметра сигнатуры метода.
     */
    ContextName name();
    /**
     * Обязательное.
     */
    boolean isRequired();
    /**
     * Допустимые типы.
     */
    List<IContext> types();
}
