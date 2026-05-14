package com.github._1c_syntax.bsl.context;

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.hbk.HbkContainerExtractor;
import com.github._1c_syntax.bsl.context.platform.hbk.HbkTreeParser;
import com.github._1c_syntax.bsl.context.platform.hbk.PageSource;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import com.github.eightm.lib.TableOfContent;
import lombok.Getter;

import java.io.ByteArrayInputStream;
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
        // Если рядом с ru-HBK лежит shcntx_root.hbk (на платформе так и есть
        // по умолчанию), автоматически делаем двуязычный парс: ru-имена
        // вариантов сигнатур и параметров получат en-алиасы.
        var enHbk = pathToHbk.getParent() != null
            ? pathToHbk.getParent().resolve("shcntx_root.hbk")
            : null;
        if (enHbk != null && Files.isRegularFile(enHbk)) {
            parseBilingual(enHbk);
            return;
        }
        provider = parseSingle(pathToHbk);
    }

    /**
     * Парсит два HBK (русский и английский) и объединяет результаты: ru-провайдер
     * сохраняется как основной, en-провайдер используется только для того,
     * чтобы проставить en-алиасы для имён сигнатур и параметров (TableOfContent
     * даёт алиасы только для самих сущностей; имена сигнатур и параметров
     * приходят из HTML-страниц на одном языке).
     *
     * @param enHbk путь к английской hbk (например, {@code shcntx_root.hbk}).
     */
    public void parseBilingual(Path enHbk) throws IOException {
        var ruProvider = parseSingle(pathToHbk);
        var enProvider = parseSingle(enHbk);
        com.github._1c_syntax.bsl.context.platform.BilingualMerger.merge(ruProvider, enProvider);
        provider = ruProvider;
    }

    private static PlatformContextProvider parseSingle(Path hbk) throws IOException {
        var entities = HbkContainerExtractor.extractHbkEntities(hbk);
        var pages = readFileStorageIntoMemory(entities.get("FileStorage"));
        var tree = getTreeSyntaxHelper(entities.get("PackBlock"));

        var pageSource = new PageSource.InMemory(pages);
        var contexts = new HbkTreeParser(pageSource).parse(tree);
        var built = new PlatformContextProvider(new PlatformContextStorage(contexts));

        // Карту байтов страниц больше не держим: после построения provider'а
        // все нужные данные извлечены в Context-объекты. На реальном HBK это
        // 50+ MB и сотни тысяч byte[] — освобождаем сразу.
        pages.clear();
        return built;
    }

    /**
     * Читает FileStorage (ZIP) целиком в память: {@code путь → байты}.
     * Без записи на диск. На реальном shcntx_ru.hbk (~24k файлов) ускоряет
     * фазу с десятков секунд до пары секунд.
     */
    private static java.util.Map<String, byte[]> readFileStorageIntoMemory(byte[] data) throws IOException {
        var pages = new java.util.HashMap<String, byte[]>(32 * 1024);
        var buffer = new byte[64 * 1024];
        try (var stream = new ZipInputStream(new ByteArrayInputStream(data),
                Charset.forName("windows-1251"))) {
            ZipEntry entry = stream.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    var out = new java.io.ByteArrayOutputStream(
                        entry.getSize() > 0 ? (int) entry.getSize() : 1024);
                    int len;
                    while ((len = stream.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    pages.put(entry.getName(), out.toByteArray());
                }
                entry = stream.getNextEntry();
            }
        }
        return pages;
    }

    private static TableOfContent getTreeSyntaxHelper(byte[] data) throws IOException {
        var inflatePackBlock = getInflatePackBlock(data);
        var tmpFile = Files.createTempFile("packBlock", "");
        Files.write(tmpFile, inflatePackBlock);
        return TableOfContent.readTableOfContent(tmpFile);
    }

    private static byte[] getInflatePackBlock(byte[] data) {
        try (var stream = new ZipInputStream(new ByteArrayInputStream(data))) {
            stream.getNextEntry();
            return stream.readAllBytes();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


}
