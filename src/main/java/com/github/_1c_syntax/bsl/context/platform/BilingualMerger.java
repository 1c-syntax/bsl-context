package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
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
     * в местах, где раньше alias был пустой.
     */
    public static void merge(ContextProvider ru, ContextProvider en) {
        var enIndex = indexByName(en);

        for (Context ruCtx : ru.getContexts()) {
            var enCtx = lookup(enIndex, ruCtx.name());
            if (enCtx == null) continue;

            if (ruCtx instanceof ContextType ruType && enCtx instanceof ContextType enType) {
                mergeMembers(ruType, enType);
            }
        }

        // Глобальный контекст лежит отдельно.
        var ruGlobal = ru.getGlobalContext();
        var enGlobal = en.getGlobalContext();
        if (ruGlobal != null && enGlobal != null) {
            mergeGlobal(ruGlobal, enGlobal);
        }
    }

    private static void mergeMembers(ContextType ruType, ContextType enType) {
        mergeMethodsList(ruType.methods(), enType.methods());
        mergeEventsList(ruType.events(), enType.events());
        // Конструкторы — у них параметры тоже в HTML, мержим.
        mergeConstructors(ruType, enType);
    }

    private static void mergeGlobal(PlatformGlobalContext ru, PlatformGlobalContext en) {
        mergeMethodsList(ru.methods(), en.methods());
        mergeEventsList(ru.applicationEvents(), en.applicationEvents());
        mergeEventsList(ru.ordinaryApplicationEvents(), en.ordinaryApplicationEvents());
        mergeEventsList(ru.sessionModuleEvents(), en.sessionModuleEvents());
        mergeEventsList(ru.externalConnectionModuleEvents(), en.externalConnectionModuleEvents());
    }

    private static void mergeMethodsList(List<ContextMethod> ru, List<ContextMethod> en) {
        var enByName = new HashMap<String, ContextMethod>(en.size() * 2);
        for (var m : en) {
            enByName.put(m.name().getName(), m);
            enByName.put(m.name().getAlias(), m);
        }
        for (var ruM : ru) {
            var enM = enByName.get(ruM.name().getName());
            if (enM == null) enM = enByName.get(ruM.name().getAlias());
            if (enM == null) continue;
            mergeSignatures(ruM.signatures(), enM.signatures());
        }
    }

    private static void mergeEventsList(List<ContextEvent> ru, List<ContextEvent> en) {
        var enByName = new HashMap<String, ContextEvent>(en.size() * 2);
        for (var e : en) {
            enByName.put(e.name().getName(), e);
            enByName.put(e.name().getAlias(), e);
        }
        for (var ruE : ru) {
            var enE = enByName.get(ruE.name().getName());
            if (enE == null) enE = enByName.get(ruE.name().getAlias());
            if (enE == null) continue;
            mergeSignatures(ruE.signatures(), enE.signatures());
        }
    }

    private static void mergeSignatures(List<ContextMethodSignature> ru, List<ContextMethodSignature> en) {
        if (ru.size() != en.size()) return;
        for (int i = 0; i < ru.size(); i++) {
            var ruSig = ru.get(i);
            var enSig = en.get(i);
            if (ruSig instanceof PlatformContextMethodSignature ruPS
                && enSig instanceof PlatformContextMethodSignature enPS) {
                ruPS.setName(mergedName(ruSig.name(), enSig.name()));
            }
            mergeParameters(ruSig, enSig);
        }
    }

    private static void mergeParameters(ContextMethodSignature ru, ContextMethodSignature en) {
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
        }
    }

    private static void mergeConstructors(ContextType ru, ContextType en) {
        var ruCs = ru.constructors();
        var enCs = en.constructors();
        var enByName = new HashMap<String, com.github._1c_syntax.bsl.context.api.ContextConstructor>(enCs.size() * 2);
        for (var c : enCs) {
            enByName.put(c.name().getName(), c);
            enByName.put(c.name().getAlias(), c);
        }
        for (var ruC : ruCs) {
            var enC = enByName.get(ruC.name().getName());
            if (enC == null) enC = enByName.get(ruC.name().getAlias());
            if (enC == null) continue;
            var ruP = ruC.parameters();
            var enP = enC.parameters();
            if (ruP.size() != enP.size()) continue;
            for (int i = 0; i < ruP.size(); i++) {
                if (ruP.get(i) instanceof PlatformContextSignatureParameter ruPS
                    && enP.get(i) instanceof PlatformContextSignatureParameter enPS) {
                    ruPS.setName(mergedName(ruP.get(i).name(), enP.get(i).name()));
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
