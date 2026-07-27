# bsl-context

Java-парсер синтакс-помощника (`.hbk`) платформы **1С:Предприятие 8**.
Извлекает из файлов справки полную модель: типы (включая коллекции и
формы с их параметрами), методы, свойства, события, конструкторы,
перечисления, глобальный контекст и языковые конструкции встроенного
языка (литералы, операторы, директивы компиляции, аннотации, инструкции
препроцессора) — с метаданными (версии появления и депрекации, описания,
примеры, ссылки «См. также», значения по умолчанию для параметров,
рекомендации по замене устаревших элементов).

Парсятся **оба** парных HBK платформы:
- `shcntx_*.hbk` — типы, методы, свойства, события, перечисления, глобальный контекст;
- `shlang_*.hbk` — раздел «Встроенный язык»: примитивы,
  литералы (`Истина`/`Ложь`), операторы и управляющие конструкции
  (`Если`, `Для`, `Пока`, `Попытка`, `Новый`, `?`, `[...]`, `И`/`ИЛИ`/`НЕ`),
  объявления (`Процедура`, `Функция`, `Перем`), директивы компиляции
  (`&НаКлиенте`, `&НаСервере`, …), аннотации (`&Перед`, `&После`, …),
  инструкции препроцессора (`#Если`, `#Область`, …).

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
    событиями / конструкторами, параметрами формы и описанием из СП;
  - `ContextCollection extends ContextType` — коллекции (`Массив`,
    `Соответствие`, `Структура`, `ТаблицаЗначений`, `ЭлементыФормы`, …):
    типы элементов + поддержка обхода `Для каждого` и индексатора `[...]`
    с их описаниями (блок «Элементы коллекции:» страницы типа);
  - `ContextFormParameter` — **параметры формы** (`ContextType.formParameters()`,
    непустой у `ФормаКлиентскогоПриложения`, расширений формы для справочника /
    документа / отчёта / динамического списка, системных форм сохранения и
    загрузки настроек): ключи структуры, которая передаётся в
    `ОткрытьФорму(…, ПараметрыФормы)` и читается внутри формы через
    `ЭтаФорма.Параметры`. У ключевых параметров (участвуют в ключе
    уникальности окна) взведён `isKey()`;
  - `ContextEnum` / `ContextEnumValue` — системные перечисления и
    их значения; у enum-«библиотек» (`БиблиотекаКартинок`, `ЦветаСтиля`, …)
    заполнен `valueType()` — общий тип всех значений набора;
  - `ContextMethod` (в т.ч. флаг `isAsync()` для `…Асинх` / `…Async`),
    `ContextProperty` (с `accessMode()` и per-property
    `collectionElementTypes()`), `ContextEvent`, `ContextConstructor`;
  - `ContextMethodSignature` (с поддержкой нескольких вариантов
    синтаксиса) и `ContextSignatureParameter` (`isRequired()`,
    `defaultValue()`, `isVariadic()` для форм `<Знач1>,...,<ЗначN>`);
  - `PlatformGlobalContext` — глобальный контекст (top-level методы,
    свойства, события приложения / обычного приложения / сеанса /
    внешнего соединения);
  - `ContextLanguageKeyword` + `LanguageKeywordCategory`
    (`LITERAL`, `STATEMENT`, `OPERATOR`, `DECLARATION`, `PRAGMA`,
    `ANNOTATION`, `PREPROCESSOR_INSTRUCTION`) +
    `LanguageKeywordSnippet` (двуязычный шаблон автодополнения
    с плейсхолдерами `<?>`);
  - `KnownStandardAttributes` — стандартные реквизиты MD-объектов
    (`Ссылка`, `ПометкаУдаления`, `Проведен`, …) по типу-владельцу:
    их состав знает только платформа, в СП и mdclasses его нет.
- **Примитивные типы** — `Строка`, `Число`, `Дата`, `Булево`, `Тип`,
  `Null`, `Неопределено` — приходят как `ContextKind.PRIMITIVE_TYPE` со
  своими описаниями из синтакс-помощника. `Произвольный` (псевдо-маркер
  «любой тип») у платформы отдельной страницы не имеет и публикуется
  как синтетический примитив с тем же `kind`.
- **Метаданные:** `sinceVersion`, `deprecatedSinceVersion`,
  `recommendedReplacements`, `description`, `notes` («Примечание:» /
  «Замечание:»), `availabilities`, `examples` («Пример:»), `seeAlso`
  («См. также:» — имена квалифицируются владельцем: `Владелец.Член`).
  Снимаются не только с member-страниц, но и с главных страниц
  **типов, коллекций и перечислений** — то есть у `Массив` есть
  `sinceVersion() == "8.0"`, доступность по клиентам и пример кода,
  а у `ГруппировкаКолонок` — описание, доступность и «См. также:».
  Специфичные для элемента: `returnValueDescription` и `isAsync` метода,
  `syntaxText` (сырая строка `Синтаксис:`), `defaultValue` / `isVariadic`
  параметра, `accessMode` свойства, `isKey` параметра формы.
- **Generic-типы.** Типы вида `СправочникСсылка.<Имя справочника>` и
  свойства вида `СправочникиМенеджер :: <Имя справочника>` —
  плейсхолдеры, конкретизация которых приходит из конфигурации и
  парсится отдельным проектом
  [`MDClasses`](https://github.com/1c-syntax/mdclasses). Все такие
  элементы помечены флагом `isGeneric()` через эвристику в
  [`ContextNames`](src/main/java/com/github/_1c_syntax/bsl/context/api/ContextNames.java).
- **Имя контекста — с заголовка страницы** (`V8SH_pagetitle`), а не из
  оглавления HBK: в оглавлении узел назван относительно родителя
  («Поле ввода» → «Расширение»), что вне дерева бессмысленно и вдобавок
  неуникально. На странице стоит полное имя — «Расширение поля ввода
  системного перечисления». В 8.3.27 так уточняются 209 типов из 2420,
  и число неуникальных имён падает с 27 до 10. Одна страница даёт ровно
  один контекст, даже если в оглавлении она висит несколькими узлами.
- **Омонимы.** Оставшиеся совпадения имён — от самой платформы: например,
  «Расширение элементов управления, расположенных в форме» существует
  отдельно для обычных (8.0) и управляемых (8.2) форм, причём и ru-, и
  en-имена у них одинаковые. Оба контекста есть в модели;
  `getContextByName` вернёт какой-то один, а
  **`getContextsByName`** — все, различить их можно по `sinceVersion()`,
  `availabilities()` или составу членов.
- **Двуязычие (ru + en).** Имена самих сущностей приходят сразу с
  обоими языками. Имена **вариантов сигнатур** и **параметров**, а также
  все **тексты** (описания, примеры, «Замечание:», «См. также:») в одной
  HBK живут только на одном языке — для них есть
  [`BilingualMerger`](src/main/java/com/github/_1c_syntax/bsl/context/platform/BilingualMerger.java),
  который парсит обе версии (`shcntx_ru.hbk` + `shcntx_root.hbk`),
  сопоставляет контексты **по пути страницы внутри HBK** (пути ru и en
  совпадают файл-в-файл, а имена расходятся: на ru-странице в скобках
  может стоять устаревший английский вариант),
  подтягивает en-алиасы в ru-провайдер, а en-тексты кладёт рядом как
  `EnAttachments` — их отдаёт `PlatformContextProvider.getEnAttachments(x)`
  (сам объект модели остаётся ru). Языковые конструкции из
  `shlang_ru.hbk` подмешивают en-алиасы из парного `shlang_root.hbk`
  (имена body-keyword'ов вроде `Тогда`/`Then`, `КонецЕсли`/`EndIf`
  сматчиваются по позиции тегов на синхронных страницах ru/en),
  а двуязычные сниппеты автодополнения тащатся прямо из парного
  `.st`-файла.
- **Автодетект установленной платформы** —
  [`PlatformFinder`](src/main/java/com/github/_1c_syntax/bsl/context/PlatformFinder.java)
  на Windows / Linux / macOS, аналог OneScript-библиотеки `v8find`.
  Можно запросить самую свежую версию (`findLatest()`) или конкретную
  (`findVersion("8.3.27.1786")`).

---

## Быстрый старт

### Подключение зависимости

Релизы публикуются в Maven Central:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.1c-syntax:bsl-context:<version>")
}
```

SNAPSHOT'ы (сборки с `master`) — в snapshot-репозитории Central:

```kotlin
repositories {
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
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
```

`workDir` может быть `null` (и у каждого метода есть перегрузка без него)
— тогда будет использован временный каталог.

`parse()` сам собирает всё, что лежит рядом с указанным `shcntx_ru.hbk`:
парный `shcntx_root.hbk` (двуязычие — en-алиасы сигнатур/параметров и
en-тексты) и `shlang_ru.hbk` + `shlang_root.hbk` (примитивы и языковые
конструкции). Отдельно дёргать мердж нужно только если en-файл лежит
не рядом:

```java
grabber.parseBilingual(enHbkFile);
```

---

## Архитектура

```
shcntx_*.hbk                                 shlang_*.hbk
  ├─ FileStorage (ZIP) ─► in-memory Map        └─ FileStorage (плоский,
  └─ PackBlock  (ZIP)  ─► TableOfContent          ru+en) ─► ShlangParser
                                       │                       │
                                       ▼                       │
                              HbkTreeParser ◄──── extra:Context┘
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
                                       ▼                  (в т.ч. в shlang-примитивы)
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
| `ShlangParser` | Парсит раздел «Встроенный язык» из `shlang_*.hbk`: примитивные типы и языковые конструкции (литералы, операторы, директивы, аннотации, инструкции препроцессора). Сниппеты автодополнения и en-алиасы вытаскивает из парного `shlang_root.hbk`. |
| `PageSource` | Абстракция «открыть страницу по пути». Реализации: `InMemory` (production) и `FileSystem` (тесты на распакованных фикстурах). |
| `PlatformContextProvider` | Хранит готовые контексты, резолвит строковые ссылки в объекты `Context` и отдаёт en-тексты через `getEnAttachments`. |
| `BilingualMerger` | Подтягивает en-алиасы и en-тексты (`EnAttachments`) из en-провайдера в ru-провайдер. |
| `ContextNames` | Утилиты по именам: `isGeneric`, `typeParameters`, `familyCore`, `placeholders`. |
| `KnownStandardAttributes` | Справочник стандартных реквизитов MD-объектов (в HBK их состава нет). |

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
BSL_CONTEXT_REAL_HBK=true ./gradlew test --tests "*Smoke*"
```

Тесты автоматически находят свежую установку через `PlatformFinder`
и парсят пары `shcntx_ru.hbk` + `shcntx_root.hbk` (типы) и
`shlang_ru.hbk` + `shlang_root.hbk` (примитивы и языковые конструкции).
Проверяются: наличие ключевых типов, срабатывание generic-эвристики,
корректность двуязычного мерджа, коллекции с их элементами, `valueType`
enum-«библиотек», параметры формы, разрешение типов параметров методов
в shlang-примитивы по ссылочной идентичности.

---

## Состав модели

```
ContextProvider
├─ getContexts(): List<Context>                  // типы, коллекции, перечисления, keyword'ы
├─ getContextByName(name): Optional<Context>     // ru или en, case-insensitive; при омонимах — любой из них
├─ getContextsByName(name): List<Context>        // все омонимы (см. ниже)
└─ getGlobalContext(): PlatformGlobalContext     // top-level

PlatformContextProvider (реализация)
└─ getEnAttachments(x): EnAttachments            // en-тексты для любого элемента модели
   └─ description, returnValueDescription, notes, examples, seeAlso,
      forEachDescription, indexAccessDescription

Context
├─ name(): ContextName(ru, en)
├─ kind(): ContextKind { PRIMITIVE_TYPE, TYPE, COLLECTION, ENUM, GLOBAL_CONTEXT, LANGUAGE_KEYWORD }
├─ isGeneric(): boolean
├─ typeParameters(): List<String>                // «СправочникСсылка.<Имя справочника>» → [Имя справочника]
├─ familyCore(): String                          // → «СправочникСсылка»
│   // «страничные» метаданные главной страницы контекста:
├─ description(), notes(): String                // «Описание:», «Примечание:»/«Замечание:»
├─ availabilities(): List<Availability>          // «Доступность:»
├─ sinceVersion(), deprecatedSinceVersion(): String
├─ examples(): List<String>                      // «Пример:»
├─ seeAlso(): List<String>                       // «См. также:» → «Владелец.Член»
└─ recommendedReplacements(): List<String>

ContextType extends Context
├─ methods(): List<ContextMethod>
├─ properties(): List<ContextProperty>
├─ events(): List<ContextEvent>
├─ constructors(): List<ContextConstructor>
└─ formParameters(): List<ContextFormParameter>   // непусто только у типов-форм

ContextCollection extends ContextType
├─ collectionElementTypes(): List<Context>
├─ supportsForEach(): boolean,  forEachDescription(): String
└─ supportsIndexAccess(): boolean, indexAccessDescription(): String

ContextFormParameter
├─ name(): ContextName
├─ types(): List<Context>
├─ isKey(): boolean                              // «Использование: Ключевой»
├─ description(), sinceVersion(), deprecatedSinceVersion(): String
└─ seeAlso(), recommendedReplacements(): List<String>

ContextLanguageKeyword extends Context
├─ category(): LanguageKeywordCategory
│              { LITERAL, STATEMENT, OPERATOR, DECLARATION,
│                PRAGMA, ANNOTATION, PREPROCESSOR_INSTRUCTION }
├─ description(): String
└─ snippet(): LanguageKeywordSnippet(ru, en)   // шаблон с плейсхолдерами <?>

ContextMethod
├─ name(): ContextName
├─ description(), notes(), returnValueDescription(): String
├─ examples(), seeAlso(), recommendedReplacements(): List<String>
├─ availabilities(): List<Availability>
├─ signatures(): List<ContextMethodSignature>
├─ hasReturnValue(): boolean, returnValues(): List<Context>
├─ sinceVersion(), deprecatedSinceVersion(): String
├─ isAsync(): boolean                            // …Асинх / …Async (await-методы 8.3.18+)
└─ isGeneric(): boolean

ContextMethodSignature
├─ name(): ContextName                           // имя варианта
├─ parameters(): List<ContextSignatureParameter>
├─ description(): String
└─ syntaxText(): String                          // сырая строка «Получить(<Индекс>)»

ContextSignatureParameter
├─ name(): ContextName
├─ isRequired(): boolean
├─ isVariadic(): boolean                         // <Знач1>,...,<ЗначN> → имя-база «Знач»
├─ types(): List<Context>
├─ description(): String
└─ defaultValue(): String

ContextProperty
├─ name(): ContextName
├─ accessMode(): AccessMode { READ, READ_WRITE }
├─ types(): List<Context>
├─ collectionElementTypes(): List<Context>       // «Элементами коллекции являются объекты типа …»
├─ description(), notes(), sinceVersion(), deprecatedSinceVersion(): String
├─ seeAlso(), recommendedReplacements(): List<String>
├─ availabilities(): List<Availability>
└─ isGeneric(): boolean

ContextEvent
├─ name(): ContextName
├─ signatures(): List<ContextMethodSignature>
├─ description(), notes(), sinceVersion(), deprecatedSinceVersion(): String
├─ availabilities(): List<Availability>
├─ examples(), seeAlso(): List<String>
└─ recommendedReplacements(): List<String>

ContextConstructor
├─ name(): ContextName                           // «По количеству элементов» и т.п.
├─ parameters(): List<ContextSignatureParameter>
├─ description(), syntaxText(): String
├─ sinceVersion(), deprecatedSinceVersion(): String
├─ examples(), seeAlso(): List<String>
└─ recommendedReplacements(): List<String>

ContextEnum extends Context                      // страничные метаданные — из Context
├─ values(): List<ContextEnumValue>
└─ valueType(): Optional<ContextName>            // у enum-«библиотек»: БиблиотекаКартинок → Картинка

ContextEnumValue
├─ name(): ContextName
├─ description(), sinceVersion(), deprecatedSinceVersion(): String
└─ recommendedReplacements(): List<String>

PlatformGlobalContext extends Context
├─ methods(): List<ContextMethod>, properties(): List<ContextProperty>
├─ applicationEvents(), ordinaryApplicationEvents(),
│  sessionModuleEvents(), externalConnectionModuleEvents(): List<ContextEvent>
└─ sinceVersion(), deprecatedSinceVersion(): String

Availability { THIN_CLIENT, WEB_CLIENT, MOBILE_CLIENT, SERVER, THICK_CLIENT,
               EXTERNAL_CONNECTION, MOBILE_APPLICATION_CLIENT,
               MOBILE_APPLICATION_SERVER, MOBILE_STANDALONE_SERVER }

KnownStandardAttributes
└─ forOwner("ОбъектМетаданных: Документ"): List<ContextName>   // стандартные реквизиты

ContextNames
├─ isGeneric(name), familyCore(name), typeParameters(name)
└─ placeholders(raw): List<Placeholder>          // позиции <…> в имени
```

---

## Лицензия

LGPL-3.0-or-later.

Содержимое `.hbk`-файлов платформы 1С — собственность фирмы «1С» и
в репозиторий не включено. Тесты используют **обезличенные**
HTML-фикстуры, повторяющие разметку синтакс-помощника.
