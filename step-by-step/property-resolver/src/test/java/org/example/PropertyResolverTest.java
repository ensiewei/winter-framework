package org.example;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PropertyResolverTest {

    @Test
    public void run() {
        String home = System.getenv("JAVA_HOME");
        System.out.println("env JAVA_HOME=" + home);

        var props = new Properties();
        props.setProperty("app.title", "winter Framework");

        var pr = new PropertyResolver(props);
        assertEquals("winter Framework", pr.getProperty("${app.title}"));
        assertThrows(NullPointerException.class, () -> {
            pr.getProperty("${app.version}");
        });
        assertEquals("v1.0", pr.getProperty("${app.version:v1.0}"));
        assertEquals(1, pr.getProperty("${app.version:1}", int.class));
        assertThrows(NumberFormatException.class, () -> {
            pr.getProperty("${app.version:x}", int.class);
        });

        assertEquals(home, pr.getProperty("${app.path:${JAVA_HOME}}"));
        assertEquals(home, pr.getProperty("${app.path:${app.home:${JAVA_HOME}}}"));
        assertEquals("/not-exist", pr.getProperty("${app.path:${app.home:${ENV_NOT_EXIST:/not-exist}}}"));
    }

}
