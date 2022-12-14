package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IMPORTANT: All this only works with a proper setup and contact to Solr
 */
public class ImageExportTest {
    private static final Logger log = LoggerFactory.getLogger(ImageExportTest.class);

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }

    @Test
    void testSolrCall() throws IOException {
        QueryResponse response = ImageExport.getInstance().illustrationSolrCall("hest", 1700, 1800, -1);
        log.info("testSolrCall returns " + response.getResults().getNumFound() + " SolrDocuments as result.");
        assertTrue(0 < response.getResults().getNumFound());
    }

    @Test
    public void testIllustrationCount() throws IOException {
        QueryResponse response = ImageExport.getInstance().illustrationSolrCall("hest", 1680, 1750, -1);
        List<IllustrationMetadata> list = ImageExport.getInstance().getMetadataForIllustrations(response);
        log.info("In these documents there are " + list.size() + " illustrations.");
        assertTrue(0 < list.size());
    }

    @Test
    public void testRegexFormatting(){
        String testString1 = "id=ART88-1_SUB,x=2364,y=4484,w=652,h=100,doms_aviser_page:uuid:0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4,2938,1234";
        String testString2 = "id=ART1-2_SUB,x=2184,y=1000,w=2816,h=2804,doms_aviser_page:uuid:a2088805-cc09-4b85-a8f8-c98954d544ca,2087,2527";

        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData(testString1);
        assertEquals("ART88-1_SUB", illustration.getId());
        assertEquals(2364, illustration.getX());
        assertEquals(4484, illustration.getY());
        assertEquals(652, illustration.getW());
        assertEquals(100, illustration.getH());
        assertEquals("0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4", illustration.getPageUUID());
        assertEquals(2938, illustration.getPageWidth());
        assertEquals(1234, illustration.getPageHeight());

        illustration.setData(testString2);
        assertEquals("ART1-2_SUB", illustration.getId());
        assertEquals(2184, illustration.getX());
        assertEquals(1000, illustration.getY());
        assertEquals(2816, illustration.getW());
        assertEquals(2804, illustration.getH());
        assertEquals("a2088805-cc09-4b85-a8f8-c98954d544ca", illustration.getPageUUID());
        assertEquals(2087, illustration.getPageWidth());
        assertEquals(2527, illustration.getPageHeight());
    }

    @Test
    public void testIllustrationMetadataConversion() {
        IllustrationMetadata testIllustration = new IllustrationMetadata();
        testIllustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");

        assertEquals(IllustrationMetadata.class, testIllustration.getClass());
    }

    @Test
    public void testSingleURLConstruction() throws IOException {
        IllustrationMetadata testIllustration = new IllustrationMetadata();
        testIllustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        String serverURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");

        URL test = ImageExport.getInstance().createIllustrationLink(testIllustration);
        URL correct = new URL(serverURL+"/0/0/0/0/00001afe-9d6b-46e7-b7f3-5fb70d832d4e"+"&RGN=0.013831259,0.045385778,0.18441679,0.075642966"+"&CVT=jpeg");

        assertEquals(correct, test);
    }

    @Test
    public void testMultipleURLConstructions() throws IOException {
        // img size 2169x2644
        IllustrationMetadata illustration1 = new IllustrationMetadata();
        illustration1.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        IllustrationMetadata illustration2 = new IllustrationMetadata();
        illustration2.setData("id=ART88-1_SUB,x=1000,y=1200,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        List<IllustrationMetadata> testList = new ArrayList<>();
        testList.add(illustration1);
        testList.add(illustration2);

        List<URL> result = ImageExport.getInstance().createLinkForAllIllustrations(testList);

        HttpURLConnection connection;
        int code;
        for (int i = 0; i<result.size(); i++){
            connection = (HttpURLConnection) result.get(0).openConnection();
            connection.setRequestMethod("HEAD");
            code = connection.getResponseCode();
            assertEquals(200,code);
        }
    }

    @Test
    public void testIllustrationLink() throws IOException {
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");

        URL result = ImageExport.getInstance().createIllustrationLink(illustration);
        System.out.println(result);
    }

    @Test
    public void testSettingStartYearToLow(){
        int result = ImageExport.getInstance().setUsableStartTime(1200);
        assertEquals(1666, result);
    }

    @Test
    public void testSettingEndYearToHigh(){
        int result = ImageExport.getInstance().setUsableEndTime(1945);
        assertEquals(1880, result);
    }

    @Test
    public void testSizeConversion(){
        // Tests that ideal sizes are converted correctly
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData("id=ART88-1_SUB,x=1000,y=1200,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        String region = ImageExport.getInstance().calculateIllustrationRegion(1000, 1200, 400, 200, 2169, 2644);
        assertEquals("&RGN=0.46104196,0.45385778,0.18441679,0.075642966", region);
    }

    // Region construction contains errors as of now
    // TODO: create this test when region things are working
    //@Test
    public void testRgnConstruction(){
        String one = ImageExport.getInstance().calculateIllustrationRegion(2184,1000,2804,2816,2527,2087);
        String two = ImageExport.getInstance().calculateIllustrationRegion(468,2536,1068,1616,2527,4000);

        System.out.println(one);
        System.out.println(two);
        //&RGN=0.8642659,0.47915667,1.1096162,1.3493053
        //&RGN=0.18519984,1.2151413,0.42263553,0.7743172
        System.out.println(Math.max(2184,1000));
    }

    @Test
    public void testAltoIllustrations() throws IOException {
        // Query Solr
        QueryResponse response = ImageExport.getInstance().illustrationSolrCall("hest", 1800, 1840, 20);
        // Get illustration metadata
        List<IllustrationMetadata> illustrationMetadata = ImageExport.getInstance().getMetadataForIllustrations(response);
        List<URL> illustrationUrls = ImageExport.getInstance().createLinkForAllIllustrations(illustrationMetadata);

        for (IllustrationMetadata item: illustrationMetadata){
            System.out.println("pageUUID: " + item.getPageUUID() +
                    "\nX: " + item.getX() +
                    "\nY: " + item.getY() +
                    "\nW: " + item.getW() +
                    "\nH: " + item.getH() +
                    "\npageWidth: " + item.getPageWidth() +
                    "\npageHeight: " + item.getPageHeight() + "\n");
        }
        /*
        pageUUID: b8f6e6a0-6772-4cc0-a705-17b311b51626
        X: 616
        Y: 12020
        W: 700
        H: 684
        pageWidth: 2804
        pageHeight: 4080

        pageUUID: aac00e7d-e26a-4ab9-b169-2d0704ffce3d
        X: 1868
        Y: 1200
        W: 2212
        H: 984
        pageWidth: 3049
        pageHeight: 4701
         */

    }
}
