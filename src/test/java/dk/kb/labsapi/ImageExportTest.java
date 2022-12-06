package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
    void testSolrCall() {
        int max = 10;
        QueryResponse response = ImageExport.illustrationSolrCall("hest", max);

        SolrDocumentList responseList = response.getResults();

        for (int i = 0; i<responseList.size(); i++){
            System.out.println(responseList.get(i).jsonStr());
        }
    }

    @Test
    public void testMapping(){
        int max = 10;
        QueryResponse response = ImageExport.illustrationSolrCall("hest", max);
        SolrDocumentList responseList = response.getResults();

        for (int i = 0; i<responseList.size(); i++) {
            //System.out.println(responseList.get(i).getFieldValue("pageUUID"));
            //System.out.println(responseList.get(i).getFieldValue("page_width"));
            //System.out.println(responseList.get(i).getFieldValue("page_height"));
            //System.out.println(responseList.get(i).getFieldValue("illustration"));

            //System.out.println(responseList.get(i).jsonStr());
            //responseList.get(i).jsonStr();
            String pageUUID = responseList.get(i).getFieldValue("pageUUID").toString();
            long pageWidth = (long) responseList.get(i).getFieldValue("page_width");
            long pageHeight = (long) responseList.get(i).getFieldValue("page_height");
            List<String> illustrations = (List<String>) responseList.get(i).getFieldValue("illustration");
            responseList.get(i).getFieldValue("illustration");

            System.out.println(pageUUID + pageWidth + pageHeight + illustrations);
        }

        /*
        JSONArray responseArray = new JSONArray(jsonString);


        // Create list for all illustration values
        List<String> illustrationList = new ArrayList<>();

        for (int i = 0; i < responseArray.length(); ++i) {
            JSONObject document = responseArray.getJSONObject(i);
            String illustration = document.getString("illustration");
            String[] illustrationsSplitted = illustration.split("\n");
            String pageUUID = document.getString("pageUUID");
            int pageWidth = document.getInt("page_width");
            int pageHeight = document.getInt("page_height");

            for (int j = 0; j< illustrationsSplitted.length; j++){
                illustrationsSplitted[j] = illustrationsSplitted[j] + "," + pageUUID + "," + pageWidth + "," + pageHeight;
            }
            illustrationList.addAll(Arrays.asList(illustrationsSplitted));
        }


        for (int i = 0; i<illustrationList.size(); i++){
            System.out.println(illustrationList.get(i));
        }

         */
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
    public void testIllustrationMetadataConversion() throws IOException {
        IllustrationMetadata testIllustration = new IllustrationMetadata();
        testIllustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");

        assertEquals(IllustrationMetadata.class, testIllustration.getClass());
    }

    @Test
    public void testSingleURLConstruction() throws IOException {
        // img size 2169x2644
        IllustrationMetadata testIllustration = new IllustrationMetadata();
        testIllustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        String serverURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");

        URL test = ImageExport.createIllustrationLink(testIllustration);
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

        List<URL> result = ImageExport.createLinkForAllIllustrations(testList);
        System.out.println(result);
    }

    @Test
    public void testIllustrationLink() throws IOException {
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");

        URL result = ImageExport.createIllustrationLink(illustration);
        System.out.println(result);
    }

    @Test
    public void testSizeConversion(){
        IllustrationMetadata illustration = new IllustrationMetadata();
        illustration.setData("id=ART88-1_SUB,x=1000,y=1200,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        String region = ImageExport.calculateIllustrationRegion(1000, 1200, 400, 200, 2169, 2644);
        assertEquals("&RGN=0.46104196,0.45385778,0.18441679,0.075642966", region);
    }

    @Test
    public void testServerConfig(){
        String baseURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");
        assertFalse(baseURL.isEmpty());
    }
    @Test
    public void randomTests() throws IOException {

    }

}
