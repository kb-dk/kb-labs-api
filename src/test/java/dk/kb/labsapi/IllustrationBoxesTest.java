package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
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
        String testString = "id=ART88-1_SUB,x=2364,y=4484,w=652,h=100,doms_aviser_page:uuid:0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4,2938,1234";
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData(testString);
        assertEquals("ART88-1_SUB", illustration.getId());
        assertEquals(2364, illustration.getX());
        assertEquals(4484, illustration.getY());
        assertEquals(652, illustration.getW());
        assertEquals(100, illustration.getH());
        assertEquals("0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4", illustration.getPageUUID());
        assertEquals(2938, illustration.getPageWidth());
        assertEquals(1234, illustration.getPageHeight());
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
            System.out.println(illustration.getPageWidth());
            System.out.println(illustration.getPageHeight());
        }
    }

    @Test
    public void testGetPageUuid() throws IOException {
        String testString = ImageExtractor.solrCall();

        List<String> list = ImageExtractor.getIllustrationsList(testString);

        System.out.println(list.get(0));
    }


    @Test
    public void testDefinitionOfRegions() throws IOException {
        // img size 2169x2644
        IllustrationMetadata illustration1 = new IllustrationMetadata();
        illustration1.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        IllustrationMetadata illustration2 = new IllustrationMetadata();
        illustration2.setData("id=ART88-1_SUB,x=1000,y=1200,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");

        System.out.println(illustration1.getPageUUID());

        URL test = ImageExtractor.createIllustrationLinks(illustration1);
        System.out.println(test);

    }

    @Test
    public void testSizeConversion(){
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData("id=ART88-1_SUB,x=1000,y=1200,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");

        float calculatedX = (float) illustration.getX() / (float) illustration.getPageWidth();
        float calculatedY = (float) illustration.getY() / (float) illustration.getPageHeight();
        float calculatedW = (float) illustration.getW() / (float) illustration.getPageWidth();
        float calculatedH = (float) illustration.getH() / (float) illustration.getPageHeight();
        String region = "&RGN="+calculatedX+","+calculatedY+","+calculatedW+","+calculatedH;
        System.out.println(region);
    }
    @Test
    public void randomTests() throws IOException {

    }

}
