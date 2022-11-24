package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IMPORTANT: All this only works with a proper setup and contact to Solr
 */
public class IllustrationBoxesTest {
    private static final Logger log = LoggerFactory.getLogger(SolrTimelineTest.class);

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
    public void testRegex() throws IOException {
        IllustrationMetadata illustration = new IllustrationMetadata();
        // illustration.setData();

        // System.out.println(illustration.get);
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
