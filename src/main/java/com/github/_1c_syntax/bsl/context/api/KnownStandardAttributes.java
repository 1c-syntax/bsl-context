package com.github._1c_syntax.bsl.context.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Известные стандартные реквизиты MD-объектов 1С. Их имена не описываются ни в
 * mdclasses (там — только пользовательские реквизиты), ни в синтакс-помощнике
 * (страницы вида {@code ОбъектМетаданных: Документ.СтандартныеРеквизиты}
 * содержат лишь описание property без перечисления конкретных реквизитов:
 * «Состав стандартных реквизитов зависит от вида объекта метаданных»).
 * <p>
 * Состав знает только сама платформа 1С; этот класс — единая точка для
 * потребителей (например, language-server'а), чтобы не плодить хардкод в
 * каждом из них. Имена двуязычные, как и остальные API bsl-context.
 * <p>
 * Ключ запроса — qualifiedName типа-владельца на русской стороне
 * ({@code "ОбъектМетаданных: Документ"}, {@code "ОбъектМетаданных: Справочник"}
 * и т.п. для top-level MD; {@code "ОбъектМетаданных: ТабличнаяЧасть"} —
 * для табличных частей независимо от их родителя).
 */
public final class KnownStandardAttributes {

    private KnownStandardAttributes() {
    }

    private static final Map<String, List<ContextName>> BY_OWNER = buildIndex();

    /**
     * Возвращает стандартные реквизиты для типа-владельца по его qualifiedName
     * (русское написание). Пустой список — если тип не известен или у него нет
     * стандартных реквизитов.
     *
     * @param ownerTypeRu qualifiedName типа на русской стороне
     * @return неизменяемый список имён
     */
    public static List<ContextName> forOwner(String ownerTypeRu) {
        if (ownerTypeRu == null || ownerTypeRu.isBlank()) {
            return List.of();
        }
        var list = BY_OWNER.get(ownerTypeRu.toLowerCase(Locale.ROOT));
        return list == null ? List.of() : list;
    }

    private static Map<String, List<ContextName>> buildIndex() {
        return Map.ofEntries(
            entry("ОбъектМетаданных: Справочник", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Код", "Code"),
                name("Наименование", "Description"),
                name("Родитель", "Parent"),
                name("Владелец", "Owner"),
                name("ЭтоГруппа", "IsFolder"),
                name("Предопределенный", "Predefined"),
                name("ИмяПредопределенныхДанных", "PredefinedDataName")
            )),
            entry("ОбъектМетаданных: Документ", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Номер", "Number"),
                name("Дата", "Date"),
                name("Проведен", "Posted"),
                name("МоментВремени", "PointInTime")
            )),
            entry("ОбъектМетаданных: ПланСчетов", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Код", "Code"),
                name("Наименование", "Description"),
                name("Родитель", "Parent"),
                name("Порядок", "Order"),
                name("Вид", "Type"),
                name("Забалансовый", "OffBalance"),
                name("Предопределенный", "Predefined"),
                name("ИмяПредопределенныхДанных", "PredefinedDataName")
            )),
            entry("ОбъектМетаданных: ПланВидовРасчета", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Код", "Code"),
                name("Наименование", "Description"),
                name("ПериодДействияБазовый", "ActionPeriodIsBasic"),
                name("Предопределенный", "Predefined"),
                name("ИмяПредопределенныхДанных", "PredefinedDataName")
            )),
            entry("ОбъектМетаданных: ПланВидовХарактеристик", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Код", "Code"),
                name("Наименование", "Description"),
                name("Родитель", "Parent"),
                name("ТипЗначения", "ValueType"),
                name("ЭтоГруппа", "IsFolder"),
                name("Предопределенный", "Predefined"),
                name("ИмяПредопределенныхДанных", "PredefinedDataName")
            )),
            entry("ОбъектМетаданных: ПланОбмена", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Код", "Code"),
                name("Наименование", "Description"),
                name("ЭтотУзел", "ThisNode"),
                name("НомерОтправленного", "SentNo"),
                name("НомерПринятого", "ReceivedNo"),
                name("Предопределенный", "Predefined"),
                name("ИмяПредопределенныхДанных", "PredefinedDataName")
            )),
            entry("ОбъектМетаданных: Задача", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Номер", "Number"),
                name("Наименование", "Description"),
                name("Дата", "Date"),
                name("БизнесПроцесс", "BusinessProcess"),
                name("ТочкаМаршрута", "RoutePoint"),
                name("Выполнена", "Executed"),
                name("Предопределенный", "Predefined"),
                name("ИмяПредопределенныхДанных", "PredefinedDataName")
            )),
            entry("ОбъектМетаданных: БизнесПроцесс", List.of(
                name("Ссылка", "Ref"),
                name("ПометкаУдаления", "DeletionMark"),
                name("Номер", "Number"),
                name("Дата", "Date"),
                name("Стартован", "Started"),
                name("Завершен", "Completed"),
                name("ВедущаяЗадача", "HeadTask")
            )),
            entry("ОбъектМетаданных: Перечисление", List.of(
                name("Ссылка", "Ref"),
                name("Порядок", "Order"),
                name("Представление", "Presentation")
            )),
            entry("ОбъектМетаданных: РегистрСведений", List.of(
                name("Период", "Period"),
                name("Активность", "Active"),
                name("Регистратор", "Recorder"),
                name("НомерСтроки", "LineNumber")
            )),
            entry("ОбъектМетаданных: РегистрНакопления", List.of(
                name("Период", "Period"),
                name("Активность", "Active"),
                name("Регистратор", "Recorder"),
                name("ВидДвижения", "RecordType"),
                name("НомерСтроки", "LineNumber")
            )),
            entry("ОбъектМетаданных: РегистрБухгалтерии", List.of(
                name("Активность", "Active"),
                name("Период", "Period"),
                name("Регистратор", "Recorder"),
                name("НомерСтроки", "LineNumber")
            )),
            entry("ОбъектМетаданных: РегистрРасчета", List.of(
                name("Регистратор", "Recorder"),
                name("ВидРасчета", "CalculationType"),
                name("ПериодДействияНачало", "ActionPeriodBegin"),
                name("ПериодДействияКонец", "ActionPeriodEnd"),
                name("ПериодРегистрации", "RegistrationPeriod"),
                name("Активность", "Active"),
                name("НомерСтроки", "LineNumber")
            )),
            entry("ОбъектМетаданных: ТабличнаяЧасть", List.of(
                name("НомерСтроки", "LineNumber"),
                name("Ссылка", "Ref")
            ))
        );
    }

    private static Map.Entry<String, List<ContextName>> entry(String key, List<ContextName> value) {
        return Map.entry(key.toLowerCase(Locale.ROOT), value);
    }

    private static ContextName name(String ru, String en) {
        return new ContextName(ru, en);
    }
}
