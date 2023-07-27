package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.hbk.HbkContainerExtractor;
import com.github._1c_syntax.bsl.context.platform.hbk.HbkTreeParser;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import com.github.eightm.lib.TableOfContent;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Граббер контекста платформы.
 */
public class PlatformContextGrabber {
    private static final String BASE_DIRECTORY = System.getProperty("user.home");

    private final Path homePath;

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
        this.homePath = Path.of(BASE_DIRECTORY, ".bsl-ls", "8.3.10");
    }

    public PlatformContextGrabber(Path pathToHbk, Path homePath) {
        this.pathToHbk = pathToHbk;
        this.homePath = homePath;
    }

    public void parse() throws IOException {
        var entities = HbkContainerExtractor.extractHbkEntities(pathToHbk);

        //unpackFileStorage(homePath, entities.get("FileStorage"));
        var tree = getTreeSyntaxHelper(entities.get("PackBlock"));

        var storage = createContextStorage(homePath, tree);

        // TODO реализовать заполнение или чтение на лету
        //var storage = new PlatformContextStorage();
        provider = new PlatformContextProvider(storage);
    }

    public ContextProvider getProvider() {
        return provider;
    }

    // https://www.baeldung.com/java-compress-and-uncompress
    private static void unpackFileStorage(Path out, byte[] data) throws IOException {
        byte[] buffer = new byte[2048];

        try (var stream = new ZipInputStream(new ByteArrayInputStream(data),
                Charset.forName("windows-1251"))) {

            ZipEntry zipEntry = stream.getNextEntry();
            while (zipEntry != null) {
                File newFile = createNewFile(out.toFile(), zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = stream.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }

                zipEntry = stream.getNextEntry();
            }

        }
    }

    private static TableOfContent getTreeSyntaxHelper(byte[] data) throws IOException {
        var inflatePackBlock = getInflatePackBlock(data);
        var tmpFile = Files.createTempFile("packBlock", "");
        var out = new FileOutputStream(tmpFile.toFile());
        out.write(inflatePackBlock);
        out.flush();
        out.close();

        return TableOfContent.readTableOfContent(tmpFile);
    }
    private static File createNewFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static byte[] getInflatePackBlock(byte[] data) {
        byte[] inflateData;

        byte[] buffer = new byte[2048];

        try (var stream = new ZipInputStream(new ByteArrayInputStream(data))) {
            stream.getNextEntry();
            inflateData = stream.readAllBytes();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return inflateData;
    }

    private static PlatformContextStorage createContextStorage(Path homePath, TableOfContent tree) {
        HbkTreeParser parser = new HbkTreeParser();
        var contexts = parser.parse(tree);

        return new PlatformContextStorage(contexts);
    }


}
