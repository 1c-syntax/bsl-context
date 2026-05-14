package com.github._1c_syntax.bsl.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformFinderTest {

    @Test
    void compareVersions_orderedBySemanticParts() {
        assertThat(PlatformFinder.compareVersions("8.3.27.1786", "8.3.27.1786")).isZero();
        assertThat(PlatformFinder.compareVersions("8.3.27.1786", "8.3.27.1700")).isPositive();
        assertThat(PlatformFinder.compareVersions("8.3.27", "8.3.27.1700")).isNegative();
        assertThat(PlatformFinder.compareVersions("8.3.30", "8.3.27.9999")).isPositive();
        assertThat(PlatformFinder.compareVersions("8.3", "8.3.0.0")).isZero();
    }

    @Test
    void compareVersions_handlesNullAndBlank() {
        assertThat(PlatformFinder.compareVersions(null, "8.3.27.1786")).isNegative();
        assertThat(PlatformFinder.compareVersions("8.3", "")).isPositive();
        assertThat(PlatformFinder.compareVersions(null, null)).isZero();
    }

    @Test
    void findAllInstalls_doesNotThrowOnArbitraryEnvironment() {
        // Не делаем предположений о наличии 1С на машине, где гоняются тесты:
        // важно лишь, что метод не падает и возвращает (возможно пустой) список.
        assertThat(PlatformFinder.findAllInstalls()).isNotNull();
    }

    @Test
    void platformInstall_pathsResolveRelativeToBinDir() {
        var install = new PlatformFinder.PlatformInstall(
            "8.3.27.1786", java.nio.file.Path.of("/foo/bin"));
        assertThat(install.syntaxHelperRu().toString().replace('\\', '/'))
            .endsWith("/foo/bin/shcntx_ru.hbk");
        assertThat(install.syntaxHelperEn().toString().replace('\\', '/'))
            .endsWith("/foo/bin/shcntx_root.hbk");
    }
}
