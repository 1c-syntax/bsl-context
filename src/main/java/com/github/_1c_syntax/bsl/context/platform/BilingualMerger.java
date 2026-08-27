package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextCollection;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextFormParameter;
import com.github._1c_syntax.bsl.context.api.ContextLanguageKeyword;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.QueryContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Дополняет ru-контекст алиасами из en-контекста для тех имён, которые
 * приходят из HTML-страниц одного языка: имена вариантов сигнатур и
 * имена параметров (TableOfContent даёт оба языка только для самих
 * сущностей).
 * <p>
 * Сопоставление по позиции: одноимённый метод/событие в ru и en имеет
 * одинаковое число сигнатур и параметров в идентичном порядке, так что
 * mерж сводится к индексному обходу. При расхождении количества (бывает
 * на расширениях с разными версиями языков) — пропускаем без падения.
 */
public final class BilingualMerger {

    private BilingualMerger() {
    }

    /**
     * Прокидывает en-алиасы из {@code en} в {@code ru}. Меняет
     * {@link PlatformContextMethodSignature#setName(ContextName)} и
     * {@link PlatformContextSignatureParameter#setName(ContextName)}
     * в местах, где раньше alias был пустой. Дополнительно регистрирует
     * en-описания в {@link PlatformContextProvider#putDescriptionEn} (для
     * типов, методов, свойств, параметров, конструкторов, значений
     * перечислений), если {@code ru} — экземпляр {@link PlatformContextProvider}.
     */
    public static void merge(ContextProvider ru, ContextProvider en) {
        var enIndex = indexByName(en);
        // Основной ключ — путь страницы в HBK: он одинаков в ru и en, тогда как
        // имена расходятся (в оглавлении имя контекстное, а на ru-странице
        // в скобках может стоять устаревший английский вариант).
        var enByPath = indexByPagePath(en);
        var ruProv = ru instanceof PlatformContextProvider p ? p : null;

        for (Context ruCtx : ru.getContexts()) {
            var enCtx = enByPath.get(pagePathOf(ruCtx));
            if (enCtx == null) enCtx = lookup(enIndex, ruCtx.name());
            if (enCtx == null) continue;

            if (ruProv != null) {
                ruProv.putEnAttachments(ruCtx, attachmentsForType(enCtx));
            }

            if (ruCtx instanceof ContextType ruType && enCtx instanceof ContextType enType) {
                mergeMembers(ruType, enType, ruProv);
            } else if (ruCtx instanceof ContextEnum ruEnum && enCtx instanceof ContextEnum enEnum) {
                mergeEnumValueDescriptions(ruEnum.values(), enEnum.values(), ruProv);
            }

        }

        // Глобальный контекст лежит отдельно.
        var ruGlobal = ru.getGlobalContext();
        var enGlobal = en.getGlobalContext();
        if (ruGlobal != null && enGlobal != null) {
            mergeGlobal(ruGlobal, enGlobal, ruProv);
        }
    }

    /**
     * Полный en-аттачмент к контексту: страничные тексты (description, notes,
     * examples, seeAlso) + для коллекций forEach/indexAccess. Для языковых
     * ключевых слов ({@link ContextLanguageKeyword}) — {@link EnAttachments#EMPTY}
     * (отдельный source — отдельный shlang_*.hbk).
     */
    private static EnAttachments attachmentsForType(Context ctx) {
        if (ctx instanceof ContextCollection coll) {
            return new EnAttachments(coll.description(), "", coll.notes(),
                coll.examples(), coll.seeAlso(),
                coll.forEachDescription(), coll.indexAccessDescription());
        }
        if (ctx instanceof ContextType || ctx instanceof ContextEnum) {
            return new EnAttachments(ctx.description(), "", ctx.notes(),
                ctx.examples(), ctx.seeAlso(), "", "");
        }
        // ContextLanguageKeyword'и не приходят в en-shcntx, en-description у них
        // хранится в самом ru-PlatformLanguageKeyword.descriptionEn — LS читает
        // его напрямую, без BilingualMerger.
        return EnAttachments.EMPTY;
    }

    /**
     * Полный en-аттачмент к методу: description + returnValueDescription +
     * notes + examples + seeAlso.
     */
    private static EnAttachments attachmentsForMethod(ContextMethod m) {
        return new EnAttachments(
            m.description(),
            m.returnValueDescription(),
            m.notes(),
            m.examples(),
            m.seeAlso(),
            "", ""
        );
    }

    private static void mergeMembers(ContextType ruType, ContextType enType, PlatformContextProvider provider) {
        mergeMethodsList(ruType.methods(), enType.methods(), provider);
        mergeEventsList(ruType.events(), enType.events(), provider);
        // Конструкторы — у них параметры тоже в HTML, мержим.
        mergeConstructors(ruType, enType, provider);
        // Свойства типа — описания идут с en-страниц.
        mergePropertyDescriptions(ruType.properties(), enType.properties(), provider);
        // Параметры формы — секция «Параметры формы:» (пусто у не-форм).
        mergeFormParameterDescriptions(ruType.formParameters(), enType.formParameters(), provider);
    }

    private static void mergeGlobal(PlatformGlobalContext ru, PlatformGlobalContext en, PlatformContextProvider provider) {
        mergeMethodsList(ru.methods(), en.methods(), provider);
        mergeEventsList(ru.applicationEvents(), en.applicationEvents(), provider);
        mergeEventsList(ru.ordinaryApplicationEvents(), en.ordinaryApplicationEvents(), provider);
        mergeEventsList(ru.sessionModuleEvents(), en.sessionModuleEvents(), provider);
        mergeEventsList(ru.externalConnectionModuleEvents(), en.externalConnectionModuleEvents(), provider);
        // Глобальные свойства тоже несут описания.
        mergePropertyDescriptions(ru.properties(), en.properties(), provider);
    }

    private static void mergeMethodsList(List<ContextMethod> ru, List<ContextMethod> en, PlatformContextProvider provider) {
        var enByName = new HashMap<String, ContextMethod>(en.size() * 2);
        for (var m : en) {
            enByName.put(m.name().getName(), m);
            enByName.put(m.name().getAlias(), m);
        }
        for (var ruM : ru) {
            var enM = enByName.get(ruM.name().getName());
            if (enM == null) enM = enByName.get(ruM.name().getAlias());
            if (enM == null) continue;
            if (provider != null) {
                provider.putEnAttachments(ruM, attachmentsForMethod(enM));
            }
            mergeSignatures(ruM.signatures(), enM.signatures(), provider);
        }
    }

    private static void mergePropertyDescriptions(
            List<? extends ContextProperty> ru,
            List<? extends ContextProperty> en,
            PlatformContextProvider provider) {
        if (provider == null || ru == null || en == null) return;
        var enByName = new HashMap<String, ContextProperty>(en.size() * 2);
        for (var p : en) {
            enByName.put(p.name().getName(), p);
            enByName.put(p.name().getAlias(), p);
        }
        for (var ruP : ru) {
            var enP = enByName.get(ruP.name().getName());
            if (enP == null) enP = enByName.get(ruP.name().getAlias());
            if (enP == null) continue;
            provider.putEnAttachments(ruP, new EnAttachments(
                enP.description(), "", enP.notes(), enP.examples(), enP.seeAlso(), "", ""));
        }
    }

    /**
     * Прокидывает en-описания параметров формы. Порядок секции «Параметры
     * формы:» в ru- и en-HBK разный (сортировка по локализованному имени),
     * поэтому матчим строго по имени, а не по позиции.
     */
    private static void mergeFormParameterDescriptions(
            List<ContextFormParameter> ru,
            List<ContextFormParameter> en,
            PlatformContextProvider provider) {
        if (provider == null || ru == null || en == null) return;
        var enByName = new HashMap<String, ContextFormParameter>(en.size() * 2);
        for (var p : en) {
            enByName.put(p.name().getName(), p);
            enByName.put(p.name().getAlias(), p);
        }
        for (var ruP : ru) {
            var enP = enByName.get(ruP.name().getName());
            if (enP == null) enP = enByName.get(ruP.name().getAlias());
            if (enP == null) continue;
            provider.putEnAttachments(ruP, new EnAttachments(
                enP.description(), "", "", List.of(), enP.seeAlso(), "", ""));
        }
    }

    /**
     * Прокидывает en-описания значениям перечисления. {@link ContextEnum} —
     * не {@link ContextType}, поэтому через {@code mergeMembers} значения не
     * проходят и нуждаются в отдельном мерже.
     * <p>
     * Порядок значений в ru- и en-HBK разный (сортировка по локализованному
     * имени), поэтому матчим по имени, а не по позиции: имена значений
     * двуязычны уже на входе — они приходят из оглавления.
     */
    private static void mergeEnumValueDescriptions(
            List<ContextEnumValue> ru,
            List<ContextEnumValue> en,
            PlatformContextProvider provider) {
        if (provider == null || ru == null || en == null) return;
        var enByName = new HashMap<String, ContextEnumValue>(en.size() * 2);
        for (var v : en) {
            enByName.put(v.name().getName(), v);
            enByName.put(v.name().getAlias(), v);
        }
        for (var ruV : ru) {
            var enV = enByName.get(ruV.name().getName());
            if (enV == null) enV = enByName.get(ruV.name().getAlias());
            if (enV == null) continue;
            provider.putDescriptionEn(ruV, enV.description());
        }
    }

    private static void mergeEventsList(List<ContextEvent> ru, List<ContextEvent> en,
                                        PlatformContextProvider provider) {
        var enByName = new HashMap<String, ContextEvent>(en.size() * 2);
        for (var e : en) {
            enByName.put(e.name().getName(), e);
            enByName.put(e.name().getAlias(), e);
        }
        for (var ruE : ru) {
            var enE = enByName.get(ruE.name().getName());
            if (enE == null) enE = enByName.get(ruE.name().getAlias());
            if (enE == null) continue;
            if (provider != null) {
                provider.putEnAttachments(ruE, new EnAttachments(
                    enE.description(), "", enE.notes(), enE.examples(), enE.seeAlso(), "", ""));
            }
            mergeSignatures(ruE.signatures(), enE.signatures(), provider);
        }
    }

    private static void mergeSignatures(List<ContextMethodSignature> ru, List<ContextMethodSignature> en, PlatformContextProvider provider) {
        // Sigs ru/en — не позиционно совпадающие списки: у ТаблицаЗначений.Скопировать
        // ru даёт 2 варианта, en — 3 (общий + два конкретных), и порядок другой.
        // Матчим жадно по числу параметров: для каждой ru-sig ищем первую
        // не-съеденную en-sig с тем же params.size(). Если позиционная пара по
        // случайности совпадает по числу параметров — берём её, иначе ищем дальше.
        // Если ни одна не подходит — пропускаем ru-sig (en не подменяет).
        var consumed = new boolean[en.size()];
        for (int i = 0; i < ru.size(); i++) {
            var ruSig = ru.get(i);
            int matchIdx = -1;
            if (i < en.size() && !consumed[i]
                && ru.get(i).parameters().size() == en.get(i).parameters().size()) {
                matchIdx = i;
            } else {
                for (int j = 0; j < en.size(); j++) {
                    if (consumed[j]) continue;
                    if (en.get(j).parameters().size() == ruSig.parameters().size()) {
                        matchIdx = j;
                        break;
                    }
                }
            }
            if (matchIdx < 0) continue;
            consumed[matchIdx] = true;
            var enSig = en.get(matchIdx);
            if (ruSig instanceof PlatformContextMethodSignature ruPS
                && enSig instanceof PlatformContextMethodSignature enPS) {
                ruPS.setName(mergedName(ruSig.name(), enSig.name()));
            }
            if (provider != null) {
                provider.putDescriptionEn(ruSig, enSig.description());
            }
            mergeParameters(ruSig, enSig, provider);
        }
    }

    private static void mergeParameters(ContextMethodSignature ru, ContextMethodSignature en, PlatformContextProvider provider) {
        var ruParams = ru.parameters();
        var enParams = en.parameters();
        if (ruParams.size() != enParams.size()) return;
        for (int i = 0; i < ruParams.size(); i++) {
            var ruP = ruParams.get(i);
            var enP = enParams.get(i);
            if (ruP instanceof PlatformContextSignatureParameter ruPS
                && enP instanceof PlatformContextSignatureParameter enPS) {
                ruPS.setName(mergedName(ruP.name(), enP.name()));
            }
            if (provider != null) {
                provider.putDescriptionEn(ruP, enP.description());
            }
        }
    }

    private static void mergeConstructors(ContextType ru, ContextType en, PlatformContextProvider provider) {
        var ruCs = ru.constructors();
        var enCs = en.constructors();
        // У конструкторов имя обычно пустое (ContextName("","")) — все
        // конструкторы одного типа попадали бы под один ключ "" и поэтому
        // мерж по name не работает. Сопоставляем строго по позиции:
        // конструктор #i на ru = конструктор #i на en (порядок страниц в HBK
        // совпадает у пары ru/en).
        if (ruCs.size() != enCs.size()) return;
        for (int i = 0; i < ruCs.size(); i++) {
            var ruC = ruCs.get(i);
            var enC = enCs.get(i);
            if (provider != null) {
                provider.putEnAttachments(ruC, new EnAttachments(
                    enC.description(), "", "", enC.examples(), enC.seeAlso(), "", ""));
            }
            var ruP = ruC.parameters();
            var enP = enC.parameters();
            if (ruP.size() != enP.size()) continue;
            for (int j = 0; j < ruP.size(); j++) {
                if (ruP.get(j) instanceof PlatformContextSignatureParameter ruPS
                    && enP.get(j) instanceof PlatformContextSignatureParameter enPS) {
                    ruPS.setName(mergedName(ruP.get(j).name(), enP.get(j).name()));
                }
                if (provider != null) {
                    provider.putDescriptionEn(ruP.get(j), enP.get(j).description());
                }
            }
        }
    }

    /**
     * Объединяет два имени: для ru-имени, у которого alias пустой,
     * подставляет en-имя (взятое из ru-name en-провайдера). Если у ru уже
     * есть alias — оставляем как есть.
     */
    private static ContextName mergedName(ContextName ruName, ContextName enName) {
        if (ruName.getAlias() != null && !ruName.getAlias().isBlank()) {
            return ruName;
        }
        // В en-провайдере имя приходит из en-HBK как "ru"-имя (en-имя там основное).
        return new ContextName(ruName.getName(), enName.getName());
    }

    /**
     * Прокидывает en-имена и en-описания таблицам языка запросов, их полям и
     * параметрам. У запросного контекста нет провайдера-держателя
     * en-аттачментов, поэтому en-описание живёт в самих объектах —
     * {@code descriptionEn()}.
     * Контекст запросов живёт отдельно от контекста встроенного языка, поэтому
     * и мерж для него отдельный — вызывается из {@code PlatformContextGrabber}
     * поверх пары {@link QueryContextProvider}.
     */
    public static void mergeQueryTables(QueryContextProvider ru, QueryContextProvider en) {
        var enByPath = new HashMap<String, PlatformQueryTable>();
        for (var table : en.getTables()) {
            if (table instanceof PlatformQueryTable t && !t.pagePath().isEmpty()) {
                enByPath.put(t.pagePath(), t);
            }
        }
        for (var table : ru.getTables()) {
            if (!(table instanceof PlatformQueryTable ruTable)) continue;
            var enTable = enByPath.get(ruTable.pagePath());
            if (enTable == null) continue;
            ruTable.setDescriptionEn(enTable.description());
            ruTable.setExamplesEn(enTable.examples());
            mergeQueryTableMembers(ruTable, enTable);
        }
    }

    /**
     * Прокидывает en-имена и en-описания полям и параметрам таблицы языка
     * запросов. Сопоставление — по пути страницы члена: в ru-HBK страница
     * поля называется «Активность (Active)», в en-HBK — «Active», а порядок
     * в оглавлении отличается (сортировка по локализованному имени).
     */
    private static void mergeQueryTableMembers(PlatformQueryTable ru, PlatformQueryTable en) {
        var enFields = new HashMap<String, PlatformQueryTableField>();
        for (var f : en.fields()) {
            if (f instanceof PlatformQueryTableField pf && !pf.pagePath().isEmpty()) {
                enFields.put(pf.pagePath(), pf);
            }
        }
        for (var f : ru.fields()) {
            if (!(f instanceof PlatformQueryTableField ruField)) continue;
            var enField = enFields.get(ruField.pagePath());
            if (enField == null) continue;
            ruField.setName(mergedName(ruField.name(), enField.name()));
            ruField.setDescriptionEn(enField.description());
        }

        var enParams = new HashMap<String, PlatformQueryTableParameter>();
        for (var p : en.parameters()) {
            if (p instanceof PlatformQueryTableParameter pp && !pp.pagePath().isEmpty()) {
                enParams.put(pp.pagePath(), pp);
            }
        }
        for (var p : ru.parameters()) {
            if (!(p instanceof PlatformQueryTableParameter ruParam)) continue;
            var enParam = enParams.get(ruParam.pagePath());
            if (enParam == null) continue;
            ruParam.setName(mergedName(ruParam.name(), enParam.name()));
            ruParam.setDescriptionEn(enParam.description());
        }
    }

    /**
     * Индекс {@code путь страницы → контекст}. Пути есть только у контекстов из
     * shcntx (у shlang-примитивов и keyword'ов их нет — они матчатся по имени).
     */
    private static Map<String, Context> indexByPagePath(ContextProvider provider) {
        var map = new HashMap<String, Context>();
        for (var c : provider.getContexts()) {
            var path = pagePathOf(c);
            if (!path.isEmpty()) {
                map.putIfAbsent(path, c);
            }
        }
        return map;
    }

    /** Путь исходной HBK-страницы контекста; пусто, если контекст не из shcntx. */
    private static String pagePathOf(Context ctx) {
        if (ctx instanceof PlatformContextType t) return t.pagePath();
        if (ctx instanceof PlatformContextCollection c) return c.pagePath();
        if (ctx instanceof PlatformContextEnum e) return e.pagePath();
        if (ctx instanceof PlatformQueryTable q) return q.pagePath();
        return "";
    }

    private static Map<String, Context> indexByName(ContextProvider provider) {
        var map = new HashMap<String, Context>();
        for (var c : provider.getContexts()) {
            map.putIfAbsent(c.name().getName(), c);
            map.putIfAbsent(c.name().getAlias(), c);
        }
        return map;
    }

    private static Context lookup(Map<String, Context> index, ContextName name) {
        var byRu = index.get(name.getName());
        return byRu != null ? byRu : index.get(name.getAlias());
    }
}
