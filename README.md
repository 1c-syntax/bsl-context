# bsl-context

Java-парсер синтакс-помощника (`.hbk`) платформы **1С:Предприятие 8**.
Извлекает из файлов справки полную модель: типы, методы, свойства,
события, конструкторы, перечисления и глобальный контекст — с
метаданными (версии появления и депрекации, описания, примеры, ссылки
«См. также», значения по умолчанию для параметров, рекомендации по
замене устаревших элементов).

Предназначен для использования в инструментах статического анализа
кода 1С — в первую очередь как источник платформенных типов для
[`bsl-language-server`](https://github.com/1c-syntax/bsl-language-server).

---

## Что умеет

- **Распаковка `.hbk`** — самостоятельно вытаскивает FileStorage из
  контейнера (внутри это два вложенных ZIP) **в память**, без записи
  десятков тысяч HTML-файлов на диск. Полный парсинг русского
  синтакс-помощника современной платформы занимает порядка секунды.
- **Полная модель элементов**
  ([`api/`](src/main/java/com/github/_1c_syntax/bsl/context/api/)):
  - `ContextType` — платформенный тип со свойствами / методами /
    событиями / конструкторами;
  - `ContextEnum` / `ContextEnumValue` — системные перечисления и
    их значения;
  - `ContextMethod`, `ContextProperty`, `ContextEvent`,
    `ContextConstructor`;
  - `ContextMethodSignature` (с поддержкой нескольких вариантов
    синтаксиса) и `ContextSignatureParameter`;
  - `PlatformGlobalContext` — глобальный контекст (top-level методы,
    свойства, события приложения / обычного приложения / сеанса /
    внешнего соединения).
- **Метаданные:** `sinceVersion`, `deprecatedSinceVersion`,
  `recommendedReplacements`, `description`, `notes` («Замечание:»),
  `examples` («Пример:»), `seeAlso` («См. также:»),
  `returnValueDescription`, `syntaxText` (сырая строка `Синтаксис:`),
  `defaultValue` параметра, `accessMode` свойства, `availabilities`
  (по видам клиента).
- **Generic-типы.** Типы вида `СправочникСсылка.<Имя справочника>` и
  свойства вида `СправочникиМенеджер :: <Имя справочника>` —
  плейсхолдеры, конкретизация которых приходит из конфигурации и
  парсится отдельным проектом
  [`MDClasses`](https://github.com/1c-syntax/mdclasses). Все такие
  элементы помечены флагом `isGeneric()` через эвристику в
  [`ContextNames`](src/main/java/com/github/_1c_syntax/bsl/context/api/ContextNames.java).
- **Двуязычие (ru + en).** Имена самих сущностей приходят сразу с
  обоими языками. Имена **вариантов сигнатур** и **параметров** в
  одной HBK живут только на одном языке — для них есть
  [`BilingualMerger`](src/main/java/com/github/_1c_syntax/bsl/context/platform/BilingualMerger.java),
  который парсит обе версии (`shcntx_ru.hbk` + `shcntx_root.hbk`) и
  подтягивает en-алиасы в ru-провайдер.
- **Автодетект установленной платформы** —
  [`PlatformFinder`](src/main/java/com/github/_1c_syntax/bsl/context/PlatformFinder.java)
  на Windows / Linux / macOS, аналог OneScript-библиотеки `v8find`.
  Можно запросить самую свежую версию (`findLatest()`) или конкретную
  (`findVersion("8.3.27.1786")`).

---

## Быстрый старт

### Подключение зависимости

Через [jitpack](https://jitpack.io):

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.1c-syntax:bsl-context:<tag>")
}
```

### Минимальный пример

```java
import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;

// Автодетект самой свежей установленной платформы.
var grabber = PlatformContextGrabber.autoDetect(null);
grabber.parse();
ContextProvider ctx = grabber.getProvider();

// Резолв по имени (ru или en, регистронезависимый).
var array = ctx.getContextByName("Массив");  // или "Array"

// Перебор типов.
ctx.getContexts().stream()
    .filter(c -> c instanceof ContextType)
    .map(c -> (ContextType) c)
    .forEach(type -> {
        System.out.println(type.name() + (type.isGeneric() ? " [generic]" : ""));
        type.methods().forEach(m -> {
            var since = m.sinceVersion();
            var dep = m.deprecatedSinceVersion();
            System.out.println("  " + m.name()
                + (since.isEmpty() ? "" : " since=" + since)
                + (dep.isEmpty() ? "" : " DEPRECATED since=" + dep
                    + " → " + String.join(", ", m.recommendedReplacements())));
        });
    });

// Глобальный контекст.
ctx.getGlobalContext().methods().forEach(m -> System.out.println(m.name()));
```

### Способы создания

```java
// 1. Автодетект — берёт самую свежую установку, найденную PlatformFinder.
PlatformContextGrabber.autoDetect(workDir);

// 2. По каталогу bin платформы.
PlatformContextGrabber.fromPlatformBin(platformBin, workDir);

// 3. По явному пути к .hbk.
PlatformContextGrabber.fromHbk(hbkFile, workDir);

// Двуязычие — после parse() подтянуть en-имена сигнатур и параметров.
grabber.parseBilingual(enHbkFile);
```

`workDir` может быть `null` — тогда будет использован временный каталог.

---

## Архитектура

```
HBK
  ├─ FileStorage (ZIP)  ─►  in-memory Map<String, byte[]>
  └─ PackBlock  (ZIP)   ─►  TableOfContent (дерево страниц)
                                       │
                                       ▼
                               HbkTreeParser
                                       │  для каждой страницы
                                       ▼
                                 HtmlParser ── через PageSource
                                       │  извлекает структурные секции
                                       ▼
                          PlatformContext* объекты (rawTypes = строки)
                                       │
                                       ▼
                          PlatformContextProvider
                                       │  resolve: имена → ссылки на Context
                                       ▼
                          ContextProvider (готов к использованию)
```

Ключевые компоненты:

| Класс | Роль |
|---|---|
| `PlatformContextGrabber` | Точка входа: `fromHbk` / `fromPlatformBin` / `autoDetect` / `parseBilingual`. |
| `PlatformFinder` | Поиск установок платформы 1С на машине (v8find-аналог). |
| `HbkContainerExtractor` | Разбирает внешний `.hbk`-контейнер на FileStorage + PackBlock. |
| `HbkTreeParser` | Обходит дерево HBK и для каждой страницы строит `PlatformContext*`-объект через `HtmlParser`. |
| `HtmlParser` | Извлекает структурные секции HTML-страницы в `*Description`-DTO. |
| `PageSource` | Абстракция «открыть страницу по пути». Реализации: `InMemory` (production) и `FileSystem` (тесты на распакованных фикстурах). |
| `PlatformContextProvider` | Хранит готовые контексты и резолвит строковые ссылки в объекты `Context`. |
| `BilingualMerger` | Подтягивает en-алиасы из en-провайдера в ru-провайдер. |
| `ContextNames` | Утилита: эвристика `isGeneric(name)`. |

API-интерфейсы в
[`api/`](src/main/java/com/github/_1c_syntax/bsl/context/api/) не
зависят от реализаций и не тащат сторонних библиотек — потребитель
пишет адаптер к своей модели прямо через них.

---

## Производительность

Узкое место в наивной реализации — запись десятков тысяч HTML-файлов
на диск (особенно на NTFS). `bsl-context` обходит её через
`PageSource.InMemory`: FileStorage читается в `Map<String, byte[]>`
и парсится прямо из памяти, без затрагивания файловой системы.

Полный парсинг `shcntx_ru.hbk` современной платформы занимает порядка
1–2 секунд на ноутбуке среднего класса. После завершения парсинга
in-memory карта страниц очищается, чтобы освободить heap.

---

## Сборка

Требуется **Java 21+** (включена через Gradle toolchain).

```bash
./gradlew build               # сборка + тесты
./gradlew publishToMavenLocal # положить артефакт в ~/.m2
```

### Smoke-тест против реальной платформы

В обычный прогон не включён — требует установленной 1С. Запускать
вручную, выставив env-флаг:

```bash
BSL_CONTEXT_REAL_HBK=true ./gradlew test --tests "*RealHbkSmokeTest"
```

Тест автоматически находит свежую установку через `PlatformFinder`,
парсит её `shcntx_ru.hbk` и `shcntx_root.hbk` и проверяет:
наличие ключевых типов, срабатывание generic-эвристики, корректность
двуязычного мерджа.

---

## Состав модели

```
ContextProvider
├─ getContexts(): List<Context>                  // типы и перечисления
├─ getContextByName(name): Optional<Context>     // ru или en, case-insensitive
└─ getGlobalContext(): PlatformGlobalContext     // top-level

Context
├─ name(): ContextName(ru, en)
├─ kind(): ContextKind { PRIMITIVE_TYPE, TYPE, ENUM, GLOBAL_CONTEXT }
└─ isGeneric(): boolean

ContextType extends Context
├─ methods(): List<ContextMethod>
├─ properties(): List<ContextProperty>
├─ events(): List<ContextEvent>
└─ constructors(): List<ContextConstructor>

ContextMethod
├─ name(): ContextName
├─ description(), notes(), returnValueDescription(): String
├─ examples(), seeAlso(), recommendedReplacements(): List<String>
├─ availabilities(): List<Availability>
├─ signatures(): List<ContextMethodSignature>
├─ returnValues(): List<Context>
├─ sinceVersion(), deprecatedSinceVersion(): String
└─ isGeneric(): boolean

ContextMethodSignature
├─ name(): ContextName                           // имя варианта
├─ parameters(): List<ContextSignatureParameter>
├─ description(): String
└─ syntaxText(): String                          // сырая строка «Получить(<Индекс>)»

ContextSignatureParameter
├─ name(): ContextName
├─ isRequired(): boolean
├─ types(): List<Context>
├─ description(): String
└─ defaultValue(): String

ContextProperty
├─ accessMode(): AccessMode { READ, READ_WRITE }
├─ types(): List<Context>
├─ description(), sinceVersion(), deprecatedSinceVersion(): String
├─ recommendedReplacements(): List<String>
├─ availabilities(): List<Availability>
└─ isGeneric(): boolean

ContextEnum extends Context
└─ values(): List<ContextEnumValue>

ContextEnumValue
├─ name(): ContextName
├─ description(), sinceVersion(), deprecatedSinceVersion(): String
└─ recommendedReplacements(): List<String>
```

---

## Лицензия

LGPL-3.0-or-later.

Содержимое `.hbk`-файлов платформы 1С — собственность фирмы «1С» и
в репозиторий не включено. Тесты используют **обезличенные**
HTML-фикстуры, повторяющие разметку синтакс-помощника.
