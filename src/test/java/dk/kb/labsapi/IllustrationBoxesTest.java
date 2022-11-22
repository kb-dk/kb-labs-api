package dk.kb.labsapi;

import dk.kb.labsapi.api.impl.LabsapiService;
import dk.kb.util.yaml.YAML;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static dk.kb.labsapi.SolrExport.EXPORT_FORMAT.image;
import static dk.kb.labsapi.SolrExport.EXPORT_FORMAT.json;
import static dk.kb.labsapi.SolrExport.STRUCTURE.content;
import static dk.kb.labsapi.SolrExport.STRUCTURE.header;

public class IllustrationBoxesTest {
    private static final Logger log = LoggerFactory.getLogger(SolrTimelineTest.class);

    @Test
    public void illustrationExtractTest() {
        String query = "cykel AND lplace:København AND py:[1850 TO 1880]";

        List<String> fields = new ArrayList<>();
        fields.add("recordID");
        fields.add("alto_box");
        fields.add("illustration");

        long max = 10;

        List<String> structure = new ArrayList<>(){};
        structure.add("header");
        structure.add("content");


        LabsapiService service = new LabsapiService();
        service.exportFields(query, fields, max,structure, "image");
    }

}
