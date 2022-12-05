package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.params.GroupParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Matcher;

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
class SummariseExportTest {

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }

    @Test
    public void testIDDExtraction() {
        String INPUT = "https://www2.statsbiblioteket.dk/mediestream/avis/record/doms_aviser_page%3Auuid%3A35634e07-1e7c-4ece-99f8-c6088725881c/query/hest";
        Matcher m = SummariseExport.UUID_PATTERN.matcher(INPUT);
        assertTrue(m.matches(), "The pattern '" + SummariseExport.UUID_PATTERN.pattern() + "' should match input '" + INPUT + "'");
        assertEquals("35634e07-1e7c-4ece-99f8-c6088725881c", m.group(1));
    }

    // Not enabled per default as it only runs on the developer network with access to a test server
    //@Test
    public void testRetrieval() throws SolrServerException, IOException {
        String id = getID("hest");
        String alto = SummariseExport.getInstance().getALTO(id);
        assertTrue(alto.contains("<TextLine"), "The response should contain '<TextLine'");
    }

    // Search for the given query and return the recordID of the first hit. Only looks for material 140+ years old
    private String getID(String query) throws SolrServerException, IOException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setFilterQueries("py:[* TO 1880]");
        solrQuery.setFields("recordID");
        solrQuery.setRows(1);
        solrQuery.setFacet(false);
        solrQuery.setHighlight(false);
        solrQuery.set(GroupParams.GROUP, false);
        return SolrExport.getInstance().
                callSolr(solrQuery).
                getResults().get(0).
                getFieldValue("recordID").toString();
    }
}