package com.github._1c_syntax.bsl.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Поиск установленных платформ 1С на машине — упрощённый Java-аналог
 * OneScript-библиотеки {@code v8find}.
 * <p>
 * Сканирует стандартные локации:
 * <ul>
 *   <li>Windows: переменные среды {@code ProgramW6432}, {@code ProgramFiles(x86)},
 *       {@code ProgramFiles} → подпапки {@code 1Cv8}, {@code 1Cv82}, {@code 1Cv8t}</li>
 *   <li>Linux: {@code /opt/1C/v8.*}, {@code /opt/1cv8}</li>
 *   <li>macOS: {@code /opt/1cv8}</li>
 * </ul>
 * Внутри каждого корня ищет подпапки с именем-версией ({@code 8.3.27.1786})
 * и проверяет наличие {@code bin/shcntx_ru.hbk} как признака валидной установки.
 * <p>
 * Чтение {@code 1CEStart.cfg} (значения {@code InstalledLocation}) пока не
 * реализовано — для большинства машин достаточно сканирования стандартных
 * локаций.
 */
public final class PlatformFinder {

    private static final Pattern VERSION_DIR = Pattern.compile("8\\.\\d+\\.\\d+\\.\\d+");
    private static final List<String> WINDOWS_SUFFIXES = List.of("1Cv8", "1Cv82", "1Cv8t");

    private PlatformFinder() {
    }

    /**
     * Описание одной установленной версии платформы.
     */
    public record PlatformInstall(String version, Path binDir) implements Comparable<PlatformInstall> {

        /**
         * Путь к файлу русскоязычного синтакс-помощника
         * ({@code shcntx_ru.hbk}).
         */
        public Path syntaxHelperRu() {
            return binDir.resolve("shcntx_ru.hbk");
        }

        /**
         * Путь к файлу англоязычного синтакс-помощника
         * ({@code shcntx_root.hbk}).
         */
        public Path syntaxHelperEn() {
            return binDir.resolve("shcntx_root.hbk");
        }

        @Override
        public int compareTo(PlatformInstall other) {
            return compareVersions(this.version, other.version);
        }
    }

    /**
     * Возвращает список найденных установок платформы, отсортированный
     * от старшей версии к младшей.
     */
    public static List<PlatformInstall> findAllInstalls() {
        var roots = collectRoots();
        var installs = new ArrayList<PlatformInstall>();
        for (var root : roots) {
            scanRoot(root, installs);
        }
        installs.sort(Comparator.reverseOrder());
        return installs;
    }

    /**
     * Возвращает самую свежую (по номеру версии) установку.
     */
    public static Optional<PlatformInstall> findLatest() {
        var installs = findAllInstalls();
        return installs.isEmpty() ? Optional.empty() : Optional.of(installs.get(0));
    }

    /**
     * Возвращает установку с указанной версией (точное совпадение по строке
     * {@code 8.3.27.1786}). Пусто, если не найдена.
     */
    public static Optional<PlatformInstall> findVersion(String version) {
        return findAllInstalls().stream()
            .filter(p -> p.version().equals(version))
            .findFirst();
    }

    private static List<Path> collectRoots() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var roots = new ArrayList<Path>();
        if (os.contains("win")) {
            collectWindowsRoots(roots);
        } else if (os.contains("mac") || os.contains("darwin")) {
            roots.add(Path.of("/opt/1cv8"));
        } else {
            collectLinuxRoots(roots);
        }
        return roots;
    }

    private static void collectWindowsRoots(List<Path> roots) {
        for (var envVar : List.of("ProgramW6432", "ProgramFiles(x86)", "ProgramFiles")) {
            var value = System.getenv(envVar);
            if (value == null || value.isBlank()) continue;
            for (var suffix : WINDOWS_SUFFIXES) {
                roots.add(Path.of(value, suffix));
            }
        }
    }

    private static void collectLinuxRoots(List<Path> roots) {
        for (var path : List.of("/opt/1cv8/i386", "/opt/1cv8/x86_64")) {
            roots.add(Path.of(path));
        }
        var opt1c = Path.of("/opt/1C");
        if (Files.isDirectory(opt1c)) {
            try (Stream<Path> children = Files.list(opt1c)) {
                children
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("v8"))
                    .forEach(p -> {
                        roots.add(p.resolve("i386"));
                        roots.add(p.resolve("x86_64"));
                    });
            } catch (IOException ignored) {
                // нет доступа — пропускаем
            }
        }
    }

    private static void scanRoot(Path root, List<PlatformInstall> sink) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            children
                .filter(Files::isDirectory)
                .filter(p -> VERSION_DIR.matcher(p.getFileName().toString()).matches())
                .forEach(versionDir -> tryAddInstall(versionDir, sink));
        } catch (IOException ignored) {
            // нет доступа — пропускаем
        }
    }

    private static void tryAddInstall(Path versionDir, List<PlatformInstall> sink) {
        var binDir = versionDir.resolve("bin");
        var hbk = binDir.resolve("shcntx_ru.hbk");
        if (Files.isRegularFile(hbk)) {
            sink.add(new PlatformInstall(versionDir.getFileName().toString(), binDir));
        }
    }

    /**
     * Сравнивает две строки версий формата {@code X.Y.Z.W}. Если части
     * разной длины — недостающие считаются нулями. {@code null} → пустая
     * строка → меньше любой реальной версии.
     */
    static int compareVersions(String a, String b) {
        var aParts = splitVersion(a);
        var bParts = splitVersion(b);
        var len = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            int ai = i < aParts.length ? aParts[i] : 0;
            int bi = i < bParts.length ? bParts[i] : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int[] splitVersion(String v) {
        if (v == null || v.isBlank()) return new int[0];
        return Arrays.stream(v.split("\\."))
            .mapToInt(part -> {
                try {
                    return Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    return 0;
                }
            })
            .toArray();
    }
}
