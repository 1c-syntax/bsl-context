package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;

import java.nio.file.Path;

/**
 * Граббер контекста платформы.
 */
public class PlatformContextGrabber {
    /**
     * Путь к файлу со справкой.
     */
    private final Path pathToHbk;
    /**
     * Провайдер контекста.
     */
    private ContextProvider provider;

    public PlatformContextGrabber(Path pathToHbk) {
        this.pathToHbk = pathToHbk;
    }

    public void readHbk() {
        // TODO реализовать заполнение или чтение на лету
        var storage = new PlatformContextStorage();

        provider = new PlatformContextProvider(storage);
    }

    public ContextProvider getProvider() {
        return provider;
    }
}
