package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.labsapi.metadataFormats.FullPageMetadata;
import dk.kb.labsapi.metadataFormats.IllustrationMetadata;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IMPORTANT: All this only works with a proper setup and contact to Solr
 */
public class ImageExportTest {
    private static final Logger log = LoggerFactory.getLogger(ImageExportTest.class);

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }

    // Illustrative unit test for mocking static methods
    @Test
    void testStaticMocking() throws MalformedURLException {
        // All mocking takes place inside the try-block
        try (MockedStatic<ImageExport> mockedExport = Mockito.mockStatic(ImageExport.class)) {
            // Setup static method mock
            mockedExport.when(() -> ImageExport.setUsableEndYear(2040)).thenReturn(2023);

            // Perform unit tests
            assertEquals(2023, ImageExport.setUsableEndYear(2040),
                    "Calling a mocked static with the correct input should return the expected output");

            assertEquals(0, ImageExport.setUsableEndYear(2050),
                    "Calling a mocked static with unsupported input should return null (= 0 as int is an Atomic)");
        }
    }

    @Test
    void testInstanceMocking() throws MalformedURLException {
        // All mocking takes place inside the try-block
        // We use Mockedconstruction as we want the standard constructor for ImageExport to be evaluated
        try (MockedConstruction mocked = mockConstruction(ImageExport.class)) {
            ImageExport mockedExport = ImageExport.getInstance();
            when(mockedExport.convertPageUUID("foo")).thenReturn("bar", "bar2");
            when(mockedExport.convertPageUUID("hello")).thenReturn("world");

            // Perform unit tests using the mocked instance
            assertEquals("bar", mockedExport.convertPageUUID("foo"),
                    "Calling a mocked method with the first correct input should return the first expected output");
            assertEquals("bar2", mockedExport.convertPageUUID("foo"),
                    "Calling a mocked method a second time with the first correct input should return the second expected output");
            assertEquals("bar2", mockedExport.convertPageUUID("foo"),
                    "Calling a mocked method a third time with the first correct input should return the last output in the response chain");

            assertEquals("world", mockedExport.convertPageUUID("hello"),
                    "Calling a mocked method with the other correct input should return the expected output");

            assertNull(mockedExport.convertPageUUID("zoo"),
                    "Calling a mocked method with unexpected input should return null");
        }
    }


    @Test
    void testCsvConstructionWithMockedDownload() throws IOException {
        StreamingOutput output = testOutput();
        assertNotNull(output);

    }

    public StreamingOutput testOutput() throws MalformedURLException {
        URL testUrl = new URL("http://callisto.statsbiblioteket.dk/iipsrv/iipsrv.fcgi?FIF=/avis-show/symlinks/0/0/0/0/00005aff-ea53-46dd-bc90-0b0dd8917dbc.jp2&CVT=jpeg");
        String testResponseString = "Image1";
        byte[] testResponse = testResponseString.getBytes(StandardCharsets.UTF_8);

        try (MockedConstruction mocked = mockConstruction(ImageExport.class)) {
            ImageExport mockedExport = ImageExport.getInstance();

            when(mockedExport.downloadSingleIllustration(testUrl)).thenReturn(testResponse);

            return output -> mockedExport.exportFullpages("pageUUID:doms_aviser_page:uuid:00005aff-ea53-46dd-bc90-0b0dd8917dbc", 1666, 1800, 1, output, "fullPage");
        }
    }


    @Test
    public void testCsvSize() throws IOException {
        int zippedLength = 47;

        FileInputStream inputFile = new FileInputStream("src/test/resources/test.zip");
        String actualLength = IOUtils.toString(inputFile, StandardCharsets.UTF_8);

        assertEquals(zippedLength, actualLength.length());
    }


    @Test
    void testSolrCall() throws IOException {
        ImageExport export = ImageExport.getInstance();
        SolrQuery testQuery = export.fullpageSolrQuery("hest", 1700, 1800);
        Stream<SolrDocument> docs = export.
                streamSolr(testQuery).
                limit(10);

        long processed = docs.count();

        assertEquals(10, processed);
    }

    @Test
    public void testIllustrationMetadata() throws IOException {
        String illustrationValues = "id=ART88-1_SUB,x=2364,y=4484,w=652,h=100";
        String pageUUID = "0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4";

        IllustrationMetadata metadata = new IllustrationMetadata(illustrationValues, pageUUID, 2500, 4200);
        assertEquals("0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4", metadata.getPageUUID());
        assertEquals("ART88-1_SUB", metadata.getId());
        assertEquals(16800.0, metadata.getPageHeight());
        assertEquals(2364.0, metadata.getX());
        assertEquals(100.0, metadata.getH());
    }

    @Test
    public void testQueryForIllustrations() throws IOException {
        SolrQuery query = ImageExport.getInstance().illustrationSolrQuery("politi", 1680, 1750, 1);
        String corretQuery = "fq=recordBase:doms_aviser+AND+py:[1680+TO+1750]&fq=illustration:+[*+TO+*]&q=politi&group=false&fl=pageUUID,+illustration,+page_width,+page_height";

        assertEquals(corretQuery, query.toString());
    }

    @Test
    public void testFullPageMetadata() throws IOException {
        FullPageMetadata metadata = new FullPageMetadata("a308fb24-8ab2-4e72-92e0-0588892bdaa0", 2938L, 1234L);

        assertEquals("a308fb24-8ab2-4e72-92e0-0588892bdaa0", metadata.getPageUUID());
        assertEquals(1234, metadata.getPageHeight());
    }

    @Test
    public void testRegexFormatting() throws IOException {
        String illustrationValues1 = "id=ART88-1_SUB,x=2364,y=4484,w=652,h=100";
        String pageUUID1 = "0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4";
        String illustrationValues2 = "id=ART1-2_SUB,x=2184,y=1000,w=2816,h=2804";
        String pageUUID2 = "a2088805-cc09-4b85-a8f8-c98954d544ca";

        IllustrationMetadata illustration = new IllustrationMetadata(illustrationValues1, pageUUID1, 2938, 1234);
        assertEquals("ART88-1_SUB", illustration.getId());
        assertEquals(2364, illustration.getX());
        assertEquals(4484, illustration.getY());
        assertEquals(652, illustration.getW());
        assertEquals(100, illustration.getH());
        assertEquals("0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4", illustration.getPageUUID());
        assertEquals(11752.0, illustration.getPageWidth());
        assertEquals(4936.0, illustration.getPageHeight());

        illustration = new IllustrationMetadata(illustrationValues2, pageUUID2, 2087, 2527);
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
    public void testIllustrationRegex() throws IOException {
        String illustrationString = "id=ART88-1_SUB,x=2364,y=4484,w=652,h=100";
        String pageUUID = "0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4";
        int pageHeight = 2938;
        int pageWidth = 1234;

        IllustrationMetadata illustration = new IllustrationMetadata(illustrationString, pageUUID, pageWidth, pageHeight);
        assertEquals("ART88-1_SUB", illustration.getId());
        assertEquals("0fd7ba18-36a2-4761-b78f-bc7ff3a07ed4", illustration.getPageUUID());
    }

    @Test
    public void testIllustrationMetadataConversion() throws IOException {
        String illustration = "id=ART88-1_SUB,x=30,y=120,w=400,h=200";
        String pageUUID = "00001afe-9d6b-46e7-b7f3-5fb70d832d4e";
        IllustrationMetadata testIllustration = new IllustrationMetadata(illustration, pageUUID, 2169, 2644);

        assertEquals(IllustrationMetadata.class, testIllustration.getClass());
    }

    @Test
    public void testUrlInclusionInMetadataObjects() throws IOException {
        String illustration = "id=ART88-1_SUB,x=30,y=120,w=400,h=200";
        String pageUUID = "00001afe-9d6b-46e7-b7f3-5fb70d832d4e";
        IllustrationMetadata testIllustration = new IllustrationMetadata(illustration, pageUUID, 2169, 2644);
        FullPageMetadata testFullpage = new FullPageMetadata(pageUUID, 2169L, 2644L);

        assertNotNull(testIllustration.getImageURL());
        assertNotNull(testFullpage.getImageURL());
    }

    @Test
    public void testUrlConstructionForIllustration() throws IOException {
        String illustration = "id=ART88-1_SUB,x=30,y=120,w=400,h=200";
        String pageUUID = "00001afe-9d6b-46e7-b7f3-5fb70d832d4e";
        IllustrationMetadata testIllustration = new IllustrationMetadata(illustration, pageUUID, 2169, 2644);
        String serverURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");

        URL correctIllustrationUrl = new URL(serverURL+"/0/0/0/0/00001afe-9d6b-46e7-b7f3-5fb70d832d4e.jp2"+"&WID=100&RGN=0.00346,0.01135,0.04610,0.01891&CVT=jpeg");

        assertEquals(correctIllustrationUrl, testIllustration.getImageURL());
    }

    @Test
    public void testUrlConstructionForFullpage() throws IOException {
        String pageUUID = "00001afe-9d6b-46e7-b7f3-5fb70d832d4e";
        FullPageMetadata testFullpage = new FullPageMetadata(pageUUID, 2169L, 2644L);
        String serverURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");

        URL correctFullpageUrl = new URL(serverURL+"/0/0/0/0/00001afe-9d6b-46e7-b7f3-5fb70d832d4e.jp2"+"&CVT=jpeg");

        assertEquals(correctFullpageUrl, testFullpage.getImageURL());

    }

    @Test
    public void testSettingStartYearToLow(){
        int result = ImageExport.getInstance().setUsableStartYear(1200);
        assertEquals(1666, result);
    }

    @Test
    public void testSettingEndYearToHigh(){
        int result = ImageExport.getInstance().setUsableEndYear(1945);
        assertEquals(1880, result);
    }


    @Test
    public void testSizeConversion(){
        IllustrationMetadata metadata = new IllustrationMetadata();
        String region = metadata.calculateIllustrationRegion(1000, 1200, 400, 200, 2169, 2644);
        // Tests that ideal sizes are converted correctly
        assertEquals("&WID=100&RGN=0.46104,0.45386,0.18442,0.07564", region);
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

    @Test
    public void testStreaming() throws IOException {
        String query = "hest";
        int startTime = 1750;
        int endTime = 1780;
        int max = 10;
        SolrQuery finalQuery = ImageExport.getInstance().fullpageSolrQuery(query, startTime, endTime);
        Stream<SolrDocument> docs = ImageExport.getInstance().
                streamSolr(finalQuery).
                limit(max);
        // Get fullPage metadata
        HashSet<String> uniqueUUIDs = new HashSet<>();
        Stream<FullPageMetadata> pageMetadata = docs.map(doc -> ImageExport.getInstance().getMetadataForFullPage(doc, uniqueUUIDs));

        long processed = pageMetadata.count();
        assertEquals(10, processed);
    }

    @Test
    public void testStreamingIllustrations() throws IOException {
        ImageExport exporter = ImageExport.getInstance();
        String query = "hest";
        int startTime = 1750;
        int endTime = 1780;
        int max = 10;
        SolrQuery finalQuery = exporter.illustrationSolrQuery(query, startTime, endTime, max);
        Stream<SolrDocument> docs = exporter.
                streamSolr(finalQuery).
                limit(max);
        HashSet<String> uniqueUUIDs = new HashSet<>();
        Stream<IllustrationMetadata> illustrationMetadata = docs.flatMap(doc -> exporter.getMetadataForIllustrations(doc, uniqueUUIDs));

        long received = illustrationMetadata.
                peek(Assertions::assertNotNull).
                count();
        assertTrue(received > 0, "Some results should be received");
    }

    @Test
    public void testUuidQueryConstruction(){
        List<String> ids = new ArrayList<>(Arrays.asList("UUID1", "UUID2", "UUID3"));
        String correctQuery = "pageUUID:UUID1 OR pageUUID:UUID2 OR pageUUID:UUID3";

        SolrQuery query = ImageExport.getInstance().createUuidQuery(ids);

        assertEquals(correctQuery, query.getQuery());
    }

    /**
     * Constructs the resource located at: src/test/resources/test.zip
     */
    private void constructTestCsv() throws IOException {
        ImageExport export = ImageExport.getInstance();
        String query = "pageUUID:doms_aviser_page:uuid:00005aff-ea53-46dd-bc90-0b0dd8917dbc";
        int startYear = 1666;
        int endYear = 1880;
        int max = 20;

        SolrQuery finalQuery = export.fullpageSolrQuery(query, startYear, endYear);
        Stream<SolrDocument> docs = export.streamSolr(finalQuery);
        // Create metadata file, that has to be added to output zip
        Set<String> uniqueUUIDs = docs
                .map(doc -> ImageExport.getInstance().saveDeduplicationList(doc, new HashSet<>()))
                .filter(Objects::nonNull)
                .limit(max)
                .collect(Collectors.toSet());

        StreamingOutput csvHeader = ImageExport.getInstance().createHeaderForCsvStream();
        Stream<StreamingOutput> csvStream = ImageExport.getInstance().streamCsvOfUniqueUUIDsMetadata(uniqueUUIDs, max);

        FileOutputStream fos = new FileOutputStream("src/test/resources/test.zip");
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        export.addCsvMetadataFileToZip(csvHeader, csvStream, zipOut);
    }
}
