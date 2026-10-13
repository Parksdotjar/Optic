package dev.optic.diagnostics;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalizationTest {
    @Test void translatedNavigationKeepsKeysAndArguments() throws Exception {
        JsonObject en,fr;
        try(var r=new InputStreamReader(getClass().getResourceAsStream("/assets/optic/lang/en_us.json"),java.nio.charset.StandardCharsets.UTF_8)) { en=JsonParser.parseReader(r).getAsJsonObject(); }
        try(var r=new InputStreamReader(getClass().getResourceAsStream("/assets/optic/lang/fr_fr.json"),java.nio.charset.StandardCharsets.UTF_8)) { fr=JsonParser.parseReader(r).getAsJsonObject(); }
        assertEquals(en.keySet(),fr.keySet());
        for(String key:en.keySet()) assertEquals(en.get(key).getAsString().split("%s",-1).length,fr.get(key).getAsString().split("%s",-1).length,key);
    }
}
