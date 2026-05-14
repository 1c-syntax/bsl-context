package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.hbk.HbkContainerExtractor;
import com.github._1c_syntax.bsl.context.platform.hbk.HbkTreeParser;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import com.github.eightm.lib.TableOfContent;
import lombok.Getter;

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
 * Граббер контекста платформы. Распаковывает {@code shcntx_ru.hbk}
 * и строит {@link ContextProvider} через {@link HbkTreeParser}.
 * <p>
 * Удобные точки входа:
 * <ul>
 *   <li>{@link #fromHbk(Path, Path)} — явный путь к .hbk;</li>
 *   <li>{@link #fromPlatformBin(Path, Path)} — каталог {@code bin} платформы
 *       (например, {@code C:\Program Files\1cv8\8.3.27.1786\bin});</li>
 *   <li>{@link #autoDetect(Path)} — автодетект самой свежей установки на
 *       машине через {@link PlatformFinder}.</li>
 * </ul>
 * Если {@code workDir} не задан, используется временный каталог.
 */
public class PlatformContextGrabber {
    private final Path homePath;

    /**
     * Путь к файлу со справкой.
     */
    private final Path pathToHbk;
    /**
     * Провайдер контекста.
     */
    @Getter
    private ContextProvider provider;

    /**
     * Создаёт граббер по пути к .hbk и каталогу для распаковки.
     * Сохранён для обратной совместимости — для новых вызовов используйте
     * {@link #fromHbk(Path, Path)}.
     */
    public PlatformContextGrabber(Path pathToHbk, Path homePath) {
        this.pathToHbk = pathToHbk;
        this.homePath = homePath;
    }

    /**
     * Граббер по явному пути к .hbk-файлу синтакс-помощника.
     *
     * @param hbk      путь к {@code shcntx_ru.hbk}
     * @param workDir  каталог, в который распаковывается FileStorage; если
     *                 {@code null}, используется временный каталог
     */
    public static PlatformContextGrabber fromHbk(Path hbk, Path workDir) throws IOException {
        return new PlatformContextGrabber(hbk, resolveWorkDir(workDir));
    }

    /**
     * Граббер по каталогу {@code bin} платформы. Внутри должен лежать
     * {@code shcntx_ru.hbk}.
     */
    public static PlatformContextGrabber fromPlatformBin(Path platformBin, Path workDir) throws IOException {
        var hbk = platformBin.resolve("shcntx_ru.hbk");
        if (!java.nio.file.Files.isRegularFile(hbk)) {
            throw new IOException("shcntx_ru.hbk not found in " + platformBin);
        }
        return fromHbk(hbk, workDir);
    }

    /**
     * Автодетект — берёт самую свежую установку платформы на машине
     * (см. {@link PlatformFinder}). Бросает {@link IOException},
     * если ни одной установки не найдено.
     */
    public static PlatformContextGrabber autoDetect(Path workDir) throws IOException {
        var install = PlatformFinder.findLatest()
            .orElseThrow(() -> new IOException(
                "No 1C platform installations found on this machine. "
                    + "Use fromHbk(...) or fromPlatformBin(...) with explicit path."));
        return fromPlatformBin(install.binDir(), workDir);
    }

    private static Path resolveWorkDir(Path workDir) throws IOException {
        if (workDir != null) {
            java.nio.file.Files.createDirectories(workDir);
            return workDir;
        }
        return java.nio.file.Files.createTempDirectory("bsl-context-");
    }

    public void parse() throws IOException {
        var entities = HbkContainerExtractor.extractHbkEntities(pathToHbk);

      unpackFileStorage(homePath, entities.get("FileStorage"));
        var tree = getTreeSyntaxHelper(entities.get("PackBlock"));

        var storage = createContextStorage(homePath, tree);

        // TODO реализовать заполнение или чтение на лету
        //var storage = new PlatformContextStorage();
        provider = new PlatformContextProvider(storage);
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
        HbkTreeParser parser = new HbkTreeParser(homePath);
        var contexts = parser.parse(tree);

        return new PlatformContextStorage(contexts);
    }


}
