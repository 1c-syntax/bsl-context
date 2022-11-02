package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.api.IContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;

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
    private IContextProvider provider;

    public PlatformContextGrabber(Path pathToHbk) {
        this.pathToHbk = pathToHbk;
    }

    public void readHbk() {
        // TODO реализовать заполнение или чтение на лету
        // по хорошему должен все равно быть сторадж с кешем или чтение всего СП в сторадж
        provider = new PlatformContextProvider();
    }

    public IContextProvider getProvider() {
        return provider;
    }
}
