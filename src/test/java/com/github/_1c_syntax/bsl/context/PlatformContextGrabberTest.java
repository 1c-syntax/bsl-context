package com.github._1c_syntax.bsl.context;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PlatformContextGrabberTest {

    @Test
    void test() throws IOException {
        var tmpDir = Files.createTempDirectory("test");
        var pathToHbk = Path.of("/Users/olegtymko/data/develop/bsl-context/bsl-context/tmp/shcntx_ru.hbk");
        var grabber = new PlatformContextGrabber(pathToHbk, tmpDir);
        grabber.parse();

        var provider = grabber.getProvider();

        var array = provider.getContextByName("массив");
        var form = provider.getContextByName("Форма");
    }

}