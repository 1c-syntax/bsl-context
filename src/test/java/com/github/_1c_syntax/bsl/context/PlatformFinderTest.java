package com.github._1c_syntax.bsl.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void linuxRoots_findArm64InstallFromOneCPackages(@TempDir Path opt) throws IOException {
        var versionDir = createInstall(opt.resolve("1cv8/arm64/8.5.1.1343"));

        var installs = PlatformFinder.findInstalls(PlatformFinder.linuxRoots(opt), false);

        assertThat(installs)
            .containsExactly(new PlatformFinder.PlatformInstall("8.5.1.1343", versionDir));
    }

    @Test
    void linuxRoots_findArm64InstallUnderOpt1C(@TempDir Path opt) throws IOException {
        var versionDir = createInstall(opt.resolve("1C/v8.5/arm64/8.5.1.1343"));

        var installs = PlatformFinder.findInstalls(PlatformFinder.linuxRoots(opt), false);

        assertThat(installs)
            .containsExactly(new PlatformFinder.PlatformInstall("8.5.1.1343", versionDir));
    }

    @Test
    void linuxRoots_findX86InstallsOrderedFromLatest(@TempDir Path opt) throws IOException {
        var older = createInstall(opt.resolve("1cv8/i386/8.3.20.1000"));
        var latest = createInstall(opt.resolve("1cv8/x86_64/8.3.27.1786"));
        var middle = createInstall(opt.resolve("1C/v8.3/x86_64/8.3.25.1000"));
        // каталог без HBK установкой не считается
        Files.createDirectories(opt.resolve("1cv8/x86_64/8.3.26.1000"));

        var installs = PlatformFinder.findInstalls(PlatformFinder.linuxRoots(opt), false);

        assertThat(installs).containsExactly(
            new PlatformFinder.PlatformInstall("8.3.27.1786", latest),
            new PlatformFinder.PlatformInstall("8.3.25.1000", middle),
            new PlatformFinder.PlatformInstall("8.3.20.1000", older));
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "создание симлинков требует прав")
    void linuxRoots_currentSymlinkDoesNotDuplicateInstall(@TempDir Path opt) throws IOException {
        // раскладка образов onec-docker: /opt/1cv8/current -> каталог версии
        var versionDir = createInstall(opt.resolve("1cv8/x86_64/8.3.27.1786"));
        Files.createSymbolicLink(opt.resolve("1cv8/current"), versionDir);

        var installs = PlatformFinder.findInstalls(PlatformFinder.linuxRoots(opt), false);

        assertThat(installs)
            .containsExactly(new PlatformFinder.PlatformInstall("8.3.27.1786", versionDir));
    }

    private static Path createInstall(Path versionDir) throws IOException {
        Files.createDirectories(versionDir);
        Files.createFile(versionDir.resolve("shcntx_ru.hbk"));
        return versionDir;
    }

    @Test
    void platformInstall_pathsResolveRelativeToBinDir() {
        var install = new PlatformFinder.PlatformInstall(
            "8.3.27.1786", Path.of("/foo/bin"));
        assertThat(install.syntaxHelperRu().toString().replace('\\', '/'))
            .endsWith("/foo/bin/shcntx_ru.hbk");
        assertThat(install.syntaxHelperEn().toString().replace('\\', '/'))
            .endsWith("/foo/bin/shcntx_root.hbk");
    }
}
