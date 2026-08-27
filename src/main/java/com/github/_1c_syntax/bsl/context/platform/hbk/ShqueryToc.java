package com.github._1c_syntax.bsl.context.platform.hbk;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Оглавление раздела «Язык запросов». Лежит внутри FileStorage отдельной
 * записью (в отличие от shcntx, где оглавление — это PackBlock), в текстовом
 * формате 1С:
 * <pre>
 * {53,52,9,54,200,57,58,67,68,70,71,72,
 * {1,1,{1,1,{"#","Секция ВЫБРАТЬ (Описание запроса)"}},"/SELECTSection"}}
 *  id parent childCount дети…                заголовок                путь
 * </pre>
 * Штатный {@code TableOfContent} этот текст не берёт (падает на грамматике),
 * поэтому разбор здесь свой — формат простой и плоский: связи задаются
 * полями {@code id} / {@code parent}.
 * <p>
 * Дерево даёт то, чего нет на самих страницах: категорию элемента (ветка
 * «Keywords» / «Functions» / «Operators»), группу функции (строковые, датные,
 * математические, агрегатные, прочие) и место конструкции в тексте запроса
 * (что допустимо внутри {@code ВЫБРАТЬ}, что — внутри {@code ИТОГИ … ПО}).
 */
public final class ShqueryToc {

    /**
     * Узел оглавления.
     *
     * @param id       идентификатор узла
     * @param parentId идентификатор родителя ({@code 0} — корень)
     * @param title    заголовок узла на языке контейнера
     * @param path     путь страницы или пусто у узлов-рубрик
     */
    public record Node(int id, int parentId, String title, String path) {

        /** Имя записи в FileStorage — путь без ведущего слэша. */
        public String pageKey() {
            return path.startsWith("/") ? path.substring(1) : path;
        }
    }

    private static final Pattern ENTRY = Pattern.compile(
        "\\{(\\d+),(\\d+),(\\d+)((?:,\\d+)*),\\s*\\{1,1,\\s*\\{1,1,\\s*\\{\"#\",\"([^\"]*)\"}\\s*},\"([^\"]*)\"}\\s*}",
        Pattern.DOTALL);

    private final Map<Integer, Node> nodes;

    private ShqueryToc(Map<Integer, Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * Находит и разбирает запись оглавления среди страниц контейнера.
     * Опознаётся по содержимому: текстовый формат 1С начинается с открывающей
     * фигурной скобки и содержит маркеры заголовков вида {@code "#"}.
     *
     * @return разобранное оглавление; пустое, если записи нет или она битая
     */
    public static ShqueryToc from(Map<String, byte[]> pages) {
        for (var entry : pages.entrySet()) {
            var text = new String(entry.getValue(), StandardCharsets.UTF_8);
            if (!text.replace("﻿", "").stripLeading().startsWith("{")
                || !text.contains("{\"#\",\"")) {
                continue;
            }
            var parsed = parse(text);
            if (!parsed.isEmpty()) {
                return new ShqueryToc(parsed);
            }
        }
        return new ShqueryToc(Map.of());
    }

    private static Map<Integer, Node> parse(String text) {
        var result = new LinkedHashMap<Integer, Node>();
        var matcher = ENTRY.matcher(text);
        while (matcher.find()) {
            var id = Integer.parseInt(matcher.group(1));
            result.put(id, new Node(id, Integer.parseInt(matcher.group(2)),
                matcher.group(5), matcher.group(6)));
        }
        return result;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /** Все узлы в порядке следования в оглавлении. */
    public List<Node> nodes() {
        return List.copyOf(nodes.values());
    }

    /** Узлы, у которых есть страница. */
    public List<Node> pageNodes() {
        var result = new ArrayList<Node>();
        for (var node : nodes.values()) {
            if (!node.path().isBlank()) {
                result.add(node);
            }
        }
        return result;
    }

    /** Родитель узла или {@code null} у корневых. */
    public Node parentOf(Node node) {
        var parent = nodes.get(node.parentId());
        return parent == node ? null : parent;
    }

    /** Прямые потомки узла в порядке оглавления. */
    public List<Node> childrenOf(Node node) {
        var result = new ArrayList<Node>();
        for (var candidate : nodes.values()) {
            if (candidate.parentId() == node.id() && candidate.id() != node.id()) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Ближайший предок, чей заголовок есть в {@code titles}; {@code null},
     * если такого нет. По этому предку определяется категория элемента.
     */
    public Node ancestorByTitle(Node node, java.util.Set<String> titles) {
        var current = parentOf(node);
        var guard = 0;
        while (current != null && guard++ < 32) {
            if (titles.contains(current.title())) {
                return current;
            }
            current = parentOf(current);
        }
        return null;
    }

    /** Первый узел, ведущий на указанную страницу. */
    public Node byPath(String path) {
        for (var node : nodes.values()) {
            if (node.path().equals(path)) {
                return node;
            }
        }
        return null;
    }
}
