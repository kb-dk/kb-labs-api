package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import dk.kb.labsapi.config.ServiceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    public void testSingleIllustrationExtraction() throws IOException {
        int[] result = ImageExtractor.getMetadataForSingleIllustration(0);
        for (int i : result) {
            System.out.println(i);
        }
    }

    @Test
    public void testAllIllustrationExtraction() throws IOException{
        int[][] result = ImageExtractor.getMetadataForAllIllustrations();
        for (int[] i : result){
            System.out.println(i);
            for (int j : i){
                System.out.println(j);
            }
        }
    }

}
