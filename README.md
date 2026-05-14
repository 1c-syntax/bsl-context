# bsl-context

Java-парсер синтакс-помощника (`.hbk`) платформы **1С:Предприятие 8**.
Извлекает из файлов справки полную модель: типы, методы, свойства,
события, конструкторы, перечисления, глобальный контекст — с
метаданными (версии, deprecated, описания, примеры, ссылки «См. также»,
значения по умолчанию для параметров).

Используется как источник платформенных типов для проекта
[`bsl-language-server`](https://github.com/1c-syntax/bsl-language-server)

---

## Что умеет

- **Распаковка `.hbk`** — самостоятельно вытаскивает FileStorage из контейнера
  (внутри это два вложенных ZIP) **в память**, без записи 24k файлов на диск.
  Полный парсинг `shcntx_ru.hbk` 8.3.27 — около **1.7 секунды** на ноутбуке.
- **Полная модель элементов** ([`api/`](src/main/java/com/github/_1c_syntax/bsl/context/api/)):
  - `ContextType` — платформенный тип со свойствами/методами/событиями/конструкторами;
  - `ContextEnum`/`ContextEnumValue` — системные перечисления;
  - `ContextMethod`, `ContextProperty`, `ContextEvent`, `ContextConstructor`;
  - `ContextMethodSignature` (с поддержкой нескольких вариантов синтаксиса)
    и `ContextSignatureParameter`;
  - `PlatformGlobalContext` — глобальный контекст (top-level методы, свойства,
    события приложения/обычного приложения/сеанса/внешнего соединения).
- **Метаданные:** `sinceVersion`, `deprecatedSinceVersion`, `description`,
  `notes` («Замечание:»), `examples` («Пример:»), `seeAlso` («См. также:»),
  `returnValueDescription`, `syntaxText` (сырая строка `Синтаксис:`),
  `defaultValue` параметра, `accessMode` свойства, `availabilities` (по
  видам клиента).
- **Generic-типы** — типы вида `СправочникСсылка.<Имя справочника>` и свойства
  вида `СправочникиМенеджер :: <Имя справочника>` (плейсхолдеры, конкретизация
  приходит из конфигурации, парсится отдельным проектом
  [`MDClasses`](https://github.com/1c-syntax/mdclasses)). Маркируются флагом
  `isGeneric()` через эвристику в [`ContextNames`](src/main/java/com/github/_1c_syntax/bsl/context/api/ContextNames.java).
  На 8.3.27.1786 эвристика находит **121** generic-тип и **243** generic-свойства,
  ложных срабатываний нет.
- **Двуязычие (ru + en).** TableOfContent даёт ru- и en-имена самих сущностей
  (`Массив`/`Array`). Имена **вариантов сигнатур** и **параметров** живут
  в HTML на одном языке — для них есть
  [`BilingualMerger`](src/main/java/com/github/_1c_syntax/bsl/context/platform/BilingualMerger.java),
  который парсит обе HBK (`shcntx_ru.hbk` + `shcntx_root.hbk`) и подтягивает
  en-алиасы в ru-провайдер.
- **Автодетект установленной платформы** — [`PlatformFinder`](src/main/java/com/github/_1c_syntax/bsl/context/PlatformFinder.java)
  на Windows/Linux/macOS, аналог OneScript-библиотеки `v8find`. Можно
  запросить самую свежую версию (`findLatest()`) или конкретную
  (`findVersion("8.3.27.1786")`).

---

## Быстрый старт

### Получение зависимости

Через jitpack (после первого тэга):

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.1c-syntax:bsl-context:<tag>")
}
```

### Использование

```java
import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;

// Вариант 1: автодетект самой свежей установленной платформы (Win/Linux/macOS).
var grabber = PlatformContextGrabber.autoDetect(null); // null = временный каталог
grabber.parse();
ContextProvider ctx = grabber.getProvider();

// Вариант 2: явный путь к каталогу bin платформы.
var grabber2 = PlatformContextGrabber.fromPlatformBin(
    Path.of("C:/Program Files/1cv8/8.3.27.1786/bin"), null);
grabber2.parse();

// Вариант 3: явный путь к .hbk-файлу.
var grabber3 = PlatformContextGrabber.fromHbk(
    Path.of("/path/to/shcntx_ru.hbk"), null);
grabber3.parse();

// Двуязычие — дополнительно подтягиваем en-имена из shcntx_root.hbk.
grabber.parseBilingual(Path.of(".../bin/shcntx_root.hbk"));

// Использование
ctx.getContexts().stream()
    .filter(c -> c instanceof ContextType)
    .map(c -> (ContextType) c)
    .forEach(type -> {
        System.out.println(type.name() + ", generic=" + type.isGeneric());
        type.methods().forEach(m -> System.out.println("  - " + m.name()
            + " since=" + m.sinceVersion()
            + (m.deprecatedSinceVersion().isEmpty() ? "" : " DEPRECATED since " + m.deprecatedSinceVersion())));
    });

// Резолв по имени (ru или en, case-insensitive).
ctx.getContextByName("Массив");      // Optional<Context>
ctx.getContextByName("Array");       // тот же тип

// Глобальный контекст
ctx.getGlobalContext().methods().forEach(m -> System.out.println(m.name()));
```

---

## Архитектура

```
HBK (.hbk file)
  │
  ├─ FileStorage (ZIP) ─────── readFileStorageIntoMemory() ─► Map<String, byte[]>
  │                                                              │
  └─ PackBlock (ZIP)   ──────  getTreeSyntaxHelper() ─►  TableOfContent
                                                              │
                          ┌───────────────────────────────────┘
                          ▼
                   HbkTreeParser
                          │ обходит дерево, для каждой страницы дёргает
                          ▼
                    HtmlParser ── через PageSource ── открывает HTML
                          │ извлекает структурные секции (Описание:, Параметры:,
                          │ Возвращаемое значение:, Доступность:, Пример:, …)
                          ▼
              PlatformContext* объекты с rawTypes (имя как строка)
                          │
                          ▼
              PlatformContextProvider
                          │ один проход processRawTypes(Map<String,Context>):
                          │ резолвит строковые ссылки в реальные Context-инстансы
                          ▼
                   ContextProvider (готов к использованию)
```

Ключевые компоненты:

| Класс | Роль |
|---|---|
| `PlatformContextGrabber` | Точка входа. `fromHbk` / `fromPlatformBin` / `autoDetect` / `parseBilingual`. |
| `PlatformFinder` | Поиск установок платформы 1С на машине (v8find-аналог). |
| `HbkContainerExtractor` | Разбирает внешний `.hbk`-контейнер на FileStorage + PackBlock. |
| `HbkTreeParser` | Обходит `TableOfContent`-дерево HBK и для каждой страницы строит `PlatformContext*`-объект через `HtmlParser`. |
| `HtmlParser` | Извлекает структурные секции HTML-страницы СП в `*Description`-DTO. |
| `PageSource` | Абстракция «открыть страницу по пути». Реализации: `InMemory` (production) и `FileSystem` (тесты на распакованных фикстурах). |
| `PlatformContextProvider` | Хранит готовые контексты, резолвит сырые имена типов в ссылки. |
| `PlatformContextStorage` | Внутренний индекс по ru/en-имени, отдельное место для `PlatformGlobalContext`. |
| `BilingualMerger` | Подтягивает en-алиасы из en-провайдера в ru-провайдер. |
| `ContextNames` | Утилита: эвристика `isGeneric(name)`. |

API-интерфейсы в [`api/`](src/main/java/com/github/_1c_syntax/bsl/context/api/) не зависят от реализаций и не тащат сторонних библиотек — потребитель (BSL LS, MDClasses, кто угодно) пишет адаптер к своей модели прямо через них.

---

## Производительность

Профиль полного парсинга `shcntx_ru.hbk` 8.3.27.1786 (Windows, Java 21):

```
extractHbkEntities   =   31 ms
readFileStorage      =  348 ms
getTreeSyntaxHelper  =  646 ms
parseHTML            =  682 ms
buildProvider        =   44 ms
total                = 1751 ms  ← было 60256 ms до in-memory PageSource
pages                = 52048
```

Главная оптимизация — `PageSource.InMemory`: HBK FileStorage читается прямо
в `Map<String, byte[]>` и парсится из памяти без записи на диск. На Windows
NTFS-метаданные на каждую из ~24 тысяч HTML-файлов добавляли 50+ секунд.

После построения `ContextProvider` карта страниц очищается
(`pages.clear()`), чтобы освободить heap.

---

## Сборка

Требуется Java 21 (toolchain в `build.gradle.kts` это форсирует).

```bash
./gradlew build              # сборка + тесты
./gradlew test               # юнит-тесты (быстрые)
./gradlew publishToMavenLocal # положить артефакт в ~/.m2
```

### Smoke-тест против реальной платформы

Не входит в обычный CI-прогон — требует установленной 1С.

```bash
BSL_CONTEXT_REAL_HBK=true ./gradlew test --tests "*RealHbkSmokeTest"
```

Тест автоматически найдёт самую свежую установку через `PlatformFinder`,
распарсит её `shcntx_ru.hbk` и `shcntx_root.hbk`, проверит наличие
ключевых типов, generic-эвристику и двуязычие.

### Дамп generic-типов

Утилита для быстрого аудита эвристики `isGeneric` на реальном HBK
живёт в throwaway-тестах (запускается вручную, отключена по умолчанию).
См. `DumpGenericsMain` пример в истории `tmp/` или собрать на основе
`PlatformContextGrabber.autoDetect()`.

---

## Тестирование

- **Юнит-тесты** работают на обезличенных HTML-фикстурах
  ([`src/test/resources/fixtures/`](src/test/resources/fixtures/)),
  повторяющих реальную HBK-разметку (классы `V8SH_*`, экранирование
  `&lt;…&gt;`, секция `<p class="V8SH_chapter">…</p>`). Имена в фикстурах
  выдуманные — `Виджет / Widget` и т.п., чтобы не класть в репозиторий
  контент платформы (см. лицензионные ограничения).
- Покрытие: ~40 тестов, проходят за <1 секунды.

---

## Состав модели

```
ContextProvider
├─ getContexts(): List<Context>      // типы + перечисления (без global)
├─ getContextByName(name): Optional<Context>  // ru или en, case-insensitive
└─ getGlobalContext(): PlatformGlobalContext  // top-level

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
├─ examples(), seeAlso(): List<String>
├─ availabilities(): List<Availability>
├─ signatures(): List<ContextMethodSignature>
├─ returnValues(): List<Context>         // разрезолвленные типы возврата
├─ sinceVersion(), deprecatedSinceVersion(): String
└─ isGeneric(): boolean

ContextMethodSignature
├─ name(): ContextName                   // имя варианта («По индексу» / …)
├─ parameters(): List<ContextSignatureParameter>
├─ description(): String
└─ syntaxText(): String                  // «Получить(<Индекс>)»

ContextSignatureParameter
├─ name(): ContextName
├─ isRequired(): boolean
├─ types(): List<Context>                // разрезолвленные типы параметра
├─ description(): String
└─ defaultValue(): String                // извлекается из описания

ContextProperty
├─ accessMode(): AccessMode { READ, READ_WRITE }
├─ types(): List<Context>
├─ description(), sinceVersion(), deprecatedSinceVersion(): String
├─ availabilities(): List<Availability>
└─ isGeneric(): boolean

ContextEnum extends Context
└─ values(): List<ContextEnumValue>

ContextEnumValue
├─ name(): ContextName
├─ description(), sinceVersion(), deprecatedSinceVersion(): String
```

---

## Лицензия

LGPL-3.0-or-later. Контент `.hbk`-файлов платформы 1С защищён собственной
лицензией 1С и в репозиторий не включён.
