package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
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
        String testString = "id=ART88-1_SUB,x=2364,y=4484,w=652,h=100 :doms_aviser_page:uuid:0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4";
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData(testString);
        assertEquals("ART88-1_SUB", illustration.getId());
        assertEquals(2364, illustration.getX());
        assertEquals(4484, illustration.getY());
        assertEquals(652, illustration.getW());
        assertEquals(100, illustration.getH());
        assertEquals("0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4", illustration.getPageUUID());
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

    @Test
    public void testGetPageUuid() throws IOException {
        String testString = ImageExtractor.solrCall();

        List<String> list = ImageExtractor.getIllustrationsList(testString);

        System.out.println(list.get(0));
    }

}
