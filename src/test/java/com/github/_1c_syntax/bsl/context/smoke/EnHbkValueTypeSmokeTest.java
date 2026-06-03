package com.github._1c_syntax.bsl.context.smoke;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.PlatformFinder;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Прицельный smoke-тест: парсит только английский HBK ({@code shcntx_root.hbk})
 * самой свежей платформы и проверяет, что
 * {@link com.github._1c_syntax.bsl.context.platform.hbk.HtmlParser#parseEnumPage}
 * корректно распознаёт английский маркер {@code "Values of this set have the type X"}.
 * <p>
 * Нужен, потому что основной {@link RealHbkSmokeTest} парсит {@code shcntx_ru.hbk}
 * и проверяет только ru-маркер; en-формулировка маркера на стенде вживую не
 * валидирована — этот тест закрывает дыру.
 * <p>
 * Запуск: {@code BSL_CONTEXT_REAL_HBK=true gradle test --tests *EnHbkValueTypeSmokeTest*}.
 */
@EnabledIfEnvironmentVariable(named = "BSL_CONTEXT_REAL_HBK", matches = "true")
class EnHbkValueTypeSmokeTest {

    @Test
    void enHbk_PictureLib_valueTypeIsPicture() throws Exception {
        var install = PlatformFinder.findLatest().orElseThrow();
        var enHbk = install.binDir().resolve("shcntx_root.hbk");
        assertThat(Files.isRegularFile(enHbk))
            .as("shcntx_root.hbk must exist at %s", enHbk)
            .isTrue();

        var workDir = Files.createTempDirectory("bsl-context-enhbk-");
        var grabber = PlatformContextGrabber.fromHbk(enHbk, workDir);
        grabber.parse();
        var provider = grabber.getProvider();

        // В EN-HBK тип называется по en-алиасу — PictureLib.
        var pictureLib = provider.getContextByName("PictureLib").orElse(null);
        assertThat(pictureLib)
            .as("PictureLib должен быть найден в EN-HBK")
            .isInstanceOf(ContextEnum.class);
        var vt = ((ContextEnum) pictureLib).valueType();
        assertThat(vt)
            .as("EN-маркер 'The values of this set have the type X' должен сработать")
            .isPresent();
        // На EN-HBK ru-имени взять неоткуда, name() и есть en-имя.
        assertThat(vt.orElseThrow().getName()).isEqualTo("Picture");

        // Дополнительно — стилевые наборы.
        for (var pair : new String[][]{
            {"ColorsOfStyle", "Color"},
            {"BordersOfStyle", "Border"},
            {"FontsOfStyle", "Font"}
        }) {
            var ctx = provider.getContextByName(pair[0]).orElse(null);
            if (!(ctx instanceof ContextEnum e)) {
                continue;
            }
            assertThat(e.valueType())
                .as("%s.valueType()", pair[0])
                .isPresent()
                .get()
                .extracting(n -> n.getName())
                .isEqualTo(pair[1]);
        }
    }
}
