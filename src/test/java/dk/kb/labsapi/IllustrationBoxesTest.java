package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import dk.kb.labsapi.config.ServiceConfig;
import org.json.JSONArray;
import org.json.JSONObject;
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


public class IllustrationBoxesTest {
    private static final Logger log = LoggerFactory.getLogger(SolrTimelineTest.class);

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }
    @Test
    public void illustrationExtractTest() throws JsonProcessingException {


    }

    @Test
    public String callSolr() throws IOException {
        // These fields act as placeholders, while the call to SolrExport.getInstance().export
        // handles all variables itself as it is for now.
        Set<String> fields = new HashSet<>();
        fields.add("alto_box");
        fields.add("illustration");
        Set<SolrExport.STRUCTURE> structure = new HashSet<>();
        structure.add(SolrExport.STRUCTURE.valueOf("content"));
        long max = 10;

        // Query here is only important variable/argument
        StreamingOutput stream = SolrExport.getInstance().export("cykel", fields, max , structure , SolrExport.EXPORT_FORMAT.image);

        // Convert StreamingOutput to String
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        stream.write(output);
        String jsonOutput = output.toString(StandardCharsets.UTF_8);
        return jsonOutput;
    }

    @Test
    public void getIllustrationLimits() throws IOException {
        String jsonResult = callSolr();

        ImageExtractor.getIllustrationIntegers(jsonResult);

    }
}
