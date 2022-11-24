package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IMPORTANT: All this only works with a proper setup and contact to Solr
 */
public class IllustrationBoxesTest {
    private static final Logger log = LoggerFactory.getLogger(IllustrationBoxesTest.class);

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }

    @Test
    void testSolrCall() throws IOException {
        String test = ImageExtractor.solrCall();
        System.out.println(test);
    }

    @Test
    public void testRegexFormatting(){
        String testString = "id=ART99-1_SUB,x=8380,y=7888,w=2596,h=448";
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData(testString);
        assertEquals("ART99-1_SUB", illustration.getId());
        assertEquals(8380, illustration.getX());
        assertEquals(7888, illustration.getY());
        assertEquals(2596, illustration.getW());
        assertEquals(448, illustration.getH());
    }

    @Test
    public void testIllustrationMetadataExtractor() throws IOException {
        List<IllustrationMetadata> illustrations = ImageExtractor.getMetadataForIllustrations();

        for (IllustrationMetadata illustration : illustrations) {
            System.out.println(illustration.getId());
            System.out.println(illustration.getX());
            System.out.println(illustration.getY());
            System.out.println(illustration.getW());
            System.out.println(illustration.getH());
        }
    }

}
