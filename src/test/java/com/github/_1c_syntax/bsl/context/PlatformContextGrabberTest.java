package com.github._1c_syntax.bsl.context;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformContextGrabberTest {

    // Синтетическое оглавление в формате справки (с BOM, как в настоящем HBK): одна страница.
    private static final String TOC = "﻿{1,\r\n"
        + "{1,0,0,{0,0,{1,0,{\"ru\",\"Глобальный контекст\"},{\"en\",\"Global context\"}},\"Global context.html\"}}}";

    @Test
    void tableOfContentTempFileIsDeletedAfterReading() throws IOException {
        // given
        var before = packBlockTempFiles();

        // when
        var tree = PlatformContextGrabber.getTreeSyntaxHelper(packBlock(TOC));

        // then
        assertThat(tree.getPages()).hasSize(1);
        assertThat(tree.getPages().get(0).title().ru()).isEqualTo("Глобальный контекст");
        assertThat(packBlockTempFiles()).isSubsetOf(before);
    }

    private static byte[] packBlock(String toc) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("PackBlock"));
            zip.write(toc.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static Set<Path> packBlockTempFiles() throws IOException {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files
                .filter(path -> path.getFileName().toString().startsWith("packBlock"))
                .collect(Collectors.toSet());
        }
    }
}
