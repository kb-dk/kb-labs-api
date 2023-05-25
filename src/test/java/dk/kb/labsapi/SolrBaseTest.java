package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
class SolrBaseTest {

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }

    @Test
    void testStreaming() {
        // Normally one would create a class that extends SolrBase. See SolrExport for an example
        SolrBase base = new SolrBase(".labsapi.aviser");

        // Create request and result stream
        SolrQuery request = new SolrQuery("hest").
                setFields("recordID", "py");
        Stream<SolrDocument> docs = base.streamSolr(request);

        // Do something with the SolrDocument stream
        long processed = docs.
                map(doc -> doc.get("recordID")).
                limit(1005). // 5 more than batch size in base.streamSolr
                count();

        assertEquals(1005, processed, "The max number of documents should be extracted");
    }
}