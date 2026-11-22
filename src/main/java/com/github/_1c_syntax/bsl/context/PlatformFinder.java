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
 * Раскладка платформы на разных OS (см. v8find/Платформа1С.os):
 * <ul>
 *   <li>Windows: {@code C:\Program Files\{1Cv8,1Cv82,1Cv8t}\<version>\bin\}
 *       — HBK в подкаталоге {@code bin/}.</li>
 *   <li>Linux: {@code /opt/1cv8/<arch>/<version>/} и
 *       {@code /opt/1C/v8.<X>/<arch>/<version>/}, где {@code <arch>} —
 *       любой подкаталог ({@code i386}, {@code x86_64}, {@code arm64}, ...) —
 *       HBK живёт <b>прямо в version-каталоге</b>, без {@code bin/}.</li>
 *   <li>macOS: {@code /opt/1cv8/<version>/} — HBK прямо в version-каталоге.</li>
 * </ul>
 * Метод {@link PlatformInstall#binDir()} возвращает каталог, в котором
 * фактически лежат файлы синтакс-помощника — то есть
 * {@code <version>/bin/} на Windows и {@code <version>/} на Linux/macOS.
 * Имя {@code binDir} сохранено по историческим причинам (исходно код был
 * написан под Windows-раскладку).
 * <p>
 * Чтение {@code 1CEStart.cfg} (значения {@code InstalledLocation}) пока не
 * реализовано — для большинства машин достаточно сканирования стандартных
 * локаций.
 */
public final class PlatformFinder {

    private static final Pattern VERSION_DIR = Pattern.compile("8\\.\\d+\\.\\d+\\.\\d+");
    private static final List<String> WINDOWS_SUFFIXES = List.of("1Cv8", "1Cv82", "1Cv8t");
    private static final boolean IS_WINDOWS =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

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
        return findInstalls(collectRoots(), IS_WINDOWS);
    }

    /**
     * Ищет установки платформы в переданных корневых каталогах: каждый
     * подкаталог корня с именем вида {@code 8.X.Y.Z}, содержащий
     * {@code shcntx_ru.hbk}, считается установкой.
     *
     * @param roots    каталоги, непосредственно содержащие version-каталоги;
     *                 несуществующие и недоступные пропускаются
     * @param hbkInBin {@code true} — файлы синтакс-помощника лежат в
     *                 подкаталоге {@code bin/} version-каталога (Windows),
     *                 {@code false} — прямо в version-каталоге (Linux/macOS)
     * @return найденные установки, от старшей версии к младшей
     */
    static List<PlatformInstall> findInstalls(List<Path> roots, boolean hbkInBin) {
        var installs = new ArrayList<PlatformInstall>();
        for (var root : roots) {
            scanRoot(root, hbkInBin, installs);
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
            roots.addAll(linuxRoots(Path.of("/opt")));
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

    /**
     * Корневые каталоги установок платформы на Linux — каталоги, непосредственно
     * содержащие version-каталоги.
     *
     * @param optDir каталог, в котором ищутся {@code 1cv8} и {@code 1C/v8.*}
     *               (в реальной системе — {@code /opt})
     * @return существующие подкаталоги {@code 1cv8} и {@code 1C/v8.*};
     *         пустой список, если ни одного нет
     */
    static List<Path> linuxRoots(Path optDir) {
        // Подкаталоги архитектур (i386, x86_64, arm64, ...) не перечисляются
        // явно: корнем считается любой подкаталог, чтобы новая архитектура
        // не требовала правки списка. Лишние подкаталоги (conf, симлинк
        // current и т.п.) безвредны — в них нет version-каталогов.
        var roots = new ArrayList<>(subdirectories(optDir.resolve("1cv8")));
        subdirectories(optDir.resolve("1C")).stream()
            .filter(p -> p.getFileName().toString().startsWith("v8"))
            .forEach(v8Dir -> roots.addAll(subdirectories(v8Dir)));
        return roots;
    }

    private static List<Path> subdirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(Files::isDirectory).sorted().toList();
        } catch (IOException ignored) {
            // нет доступа — пропускаем
            return List.of();
        }
    }

    private static void scanRoot(Path root, boolean hbkInBin, List<PlatformInstall> sink) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            children
                .filter(Files::isDirectory)
                .filter(p -> VERSION_DIR.matcher(p.getFileName().toString()).matches())
                .forEach(versionDir -> tryAddInstall(versionDir, hbkInBin, sink));
        } catch (IOException ignored) {
            // нет доступа — пропускаем
        }
    }

    private static void tryAddInstall(Path versionDir, boolean hbkInBin, List<PlatformInstall> sink) {
        // На Windows HBK живёт в подкаталоге bin/; на Linux/macOS — прямо
        // в version-каталоге (см. v8find/СоздательВерсииПлатформы.os
        // → ПолучитьПутьКФайлу, ветка ЭтоWindows / Иначе).
        var hbkDir = hbkInBin ? versionDir.resolve("bin") : versionDir;
        var hbk = hbkDir.resolve("shcntx_ru.hbk");
        if (Files.isRegularFile(hbk)) {
            sink.add(new PlatformInstall(versionDir.getFileName().toString(), hbkDir));
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
