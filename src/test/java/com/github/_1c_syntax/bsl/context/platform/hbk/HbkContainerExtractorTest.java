package com.github._1c_syntax.bsl.context.platform.hbk;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class HbkContainerExtractorTest {

    @Test
    void test() {
        // из hbk нам нужно:
        // - файл с индексами (PackBlock)
        // - контент ()

        // из индекса строим дерево
        // из контента - держим для разбора описания типов

        var path = Path.of("/Users/olegtymko/data/develop/bsl-context/bsl-context/tmp/shcntx_ru.hbk");

        var entities = HbkContainerExtractor.extractHbkEntities(path);
    }

}