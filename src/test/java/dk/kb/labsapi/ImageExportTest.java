package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.labsapi.metadataFormats.FullPageMetadata;
import dk.kb.labsapi.metadataFormats.IllustrationMetadata;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void testQueryForIllustratoins() throws IOException {
        QueryResponse response = ImageExport.getInstance().illustrationSolrCall("politi", 1680, 1750, 1);
        Object illustrations = response.getResults().get(0).getFieldValue("illustration");
        assertNotNull(illustrations);
    }

    @Test
    public void testFullPageMetadata() throws IOException {
        QueryResponse response = ImageExport.getInstance().fullpageSolrCall("politi", 1680, 1750, 1);
        List<FullPageMetadata> list = ImageExport.getInstance().getMetadataForFullPage(response);

        assertEquals("doms_aviser_page:uuid:a308fb24-8ab2-4e72-92e0-0588892bdaa0", list.get(0).getPageUUID());
        assertNotNull(list.get(0).getPageHeight());
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
        assertEquals(11752.0, illustration.getPageWidth());
        assertEquals(4936.0, illustration.getPageHeight());

        illustration.setData(testString2);
        assertEquals("ART1-2_SUB", illustration.getId());
        assertEquals(2184, illustration.getX());
        assertEquals(1000, illustration.getY());
        assertEquals(2816, illustration.getW());
        assertEquals(2804, illustration.getH());
        assertEquals("a2088805-cc09-4b85-a8f8-c98954d544ca", illustration.getPageUUID());
        assertEquals(8348.0, illustration.getPageWidth());
        assertEquals(10108.0, illustration.getPageHeight());
    }

    @Test
    public void testIllustrationMetadataConversion() {
        IllustrationMetadata testIllustration = new IllustrationMetadata();
        testIllustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");

        assertEquals(IllustrationMetadata.class, testIllustration.getClass());
    }

    @Test
    public void testSingleIllustrationURLConstruction() throws IOException {
        IllustrationMetadata testIllustration = new IllustrationMetadata();
        testIllustration.setData("id=ART88-1_SUB,x=30,y=120,w=400,h=200,doms_aviser_page:uuid:00001afe-9d6b-46e7-b7f3-5fb70d832d4e,2169,2644");
        String serverURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");

        URL test = ImageExport.getInstance().createIllustrationLink(testIllustration);
        URL correct = new URL(serverURL+"/0/0/0/0/00001afe-9d6b-46e7-b7f3-5fb70d832d4e.jp2"+"&WID=100&RGN=0.00346,0.01135,0.04610,0.01891&CVT=jpeg");

        assertEquals(correct, test);
    }

    @Test
    public void testSinglePageURLConstruction() throws IOException {
        FullPageMetadata testIllustration = new FullPageMetadata("00001afe-9d6b-46e7-b7f3-5fb70d832d4e", 2000L, 4000L);
        String serverURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");

        URL test = ImageExport.getInstance().createFullPageLink(testIllustration);
        URL correct = new URL(serverURL+"/0/0/0/0/00001afe-9d6b-46e7-b7f3-5fb70d832d4e.jp2"+"&RGN=1,1,1,1&CVT=jpeg");

        assertEquals(correct, test);
    }

    // Not enabled due to connection issues.
    //@Test
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
        assertEquals("&WID=100&RGN=0.46104,0.45386,0.18442,0.07564", region);
    }

    @Test
    public void testRgnConstruction(){
        String calculated = ImageExport.getInstance().calculateIllustrationRegion(2184,1000,2804,2816,4000,6000);
        assertEquals("&WID=701&RGN=0.54600,0.16667,0.70100,0.46933",calculated);
    }

    // Created because it looks like the ObjectWriter closes the overall ZipOutputStream in ImageExport
    @Test
    public void testZipClose() throws IOException {
        OutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(output);
        zos.setLevel(Deflater.NO_COMPRESSION);
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        ZipEntry ze = new ZipEntry("metadata.json");
        zos.putNextEntry(ze);
        writer.writeValue(zos, Map.of("foo", "bar"));

        // An exception is thrown on closeEntry with the message "Stream closed"
        Exception exception = assertThrows(IOException.class, () -> {
            zos.closeEntry();
        });

        String expectedMessage = "Stream closed";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void testAvoidZipClose() throws IOException {
        OutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(output);
        zos.setLevel(Deflater.NO_COMPRESSION);
        ObjectMapper mapper = new ObjectMapper();
        // https://stackoverflow.com/questions/66441395/jackson-objectwriter-only-writes-first-entry-from-stream
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter()).
                without(JsonGenerator.Feature.AUTO_CLOSE_TARGET); // This does the trick
        
        ZipEntry ze = new ZipEntry("metadata.json");
        zos.putNextEntry(ze);
        writer.writeValue(zos, Map.of("foo", "bar"));
        zos.closeEntry();
    }


}
