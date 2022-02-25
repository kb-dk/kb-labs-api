package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.webservice.exception.ForbiddenServiceException;
import dk.kb.webservice.exception.InvalidArgumentServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
class DataExportTest {
    private static final Logger log = LoggerFactory.getLogger(DataExportTest.class);

    final String OKAY = "doms_aviser_edition:uuid:15e8ea25-a194-4f23-bcae-a938c0292611"; // 1829
    final String NOGO = "doms_aviser_edition:uuid:69b4d5c4-ac19-4fea-837b-688c7ba0178a"; // 1963

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }

    @Test
    void testIdentifierTranslation() {
        final String[] IDS = new String[]{
                "dagbladetkoebenhavn1851 1855-09-17 001", // editionID
                "doms_aviser_edition:uuid:15e8ea25-a194-4f23-bcae-a938c0292611", // editionUUID 1
                "uuid:15e8ea25-a194-4f23-bcae-a938c0292611", // editionUUID 2
                "15e8ea25-a194-4f23-bcae-a938c0292611", // editionUUID 2
                "doms_newspaperCollection:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76-segment_19", // recordID
                "doms_aviser_page:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76" // pageUUID
        };
        final String EXPECTED = "15e8ea25-a194-4f23-bcae-a938c0292611";

        for (String id: IDS) {
            assertEquals(EXPECTED, DataExport.getInstance().pdfIdentifierToEditionUUID(id),
                         "The correct editionUUID should be extracted from id '" + id + "'");
        }

        assertThrows(InvalidArgumentServiceException.class, () -> {
            DataExport.getInstance().pdfIdentifierToEditionUUID("nonconforming");
        }, "Extracting editionUUID from identifiers of unknown format should fail");
    }

    @Test
    void testTicketResolver() {

        String downloadOK = DataExport.getInstance().resolveTicket(OKAY);
        assertFalse(downloadOK.isEmpty(), "Some ticket should be returned for edition '" + OKAY + "'");
        log.info("Got ticket '" + downloadOK + "' for OKAY '" + OKAY + "'");

        assertThrows(ForbiddenServiceException.class, () -> {
            DataExport.getInstance().resolveTicket(NOGO);
        }, "Requesting ticket for NOGO '" + NOGO + "' should fail");
    }

    @Test
    void testPDFDownload() throws IOException {
        StreamingOutput resultProvider = DataExport.getInstance().exportPDF(OKAY);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        // https://www2.statsbiblioteket.dk/newspaper-pdf/b/a/8/4/ba845c25-d733-48c4-bb8c-6d347fe62f93.pdf?ticket=dfb8388d-d7c4-4ecc-ac83-db34d0339c82&filename=Ki%C3%B8benhavns_Kongelig_alene_priviligerede_Adresse-Contoirs_Efterretninger_(1759-1854)_-_1829-10-31.pdf
        // https://www2.statsbiblioteket.dk/newspaper-pdf/1/5/e/8/15e8ea25-a194-4f23-bcae-a938c0292611.pdf?ticket=890c6b70-a364-4108-bfd9-a6c8639b9e31&filename=editionUUID_doms_aviser_edition%3Auuid%3A15e8ea25-a194-4f23-bcae-a938c0292611_through_labsapi.pdf
        resultProvider.write(result);
        assertTrue(result.size() > 1000,
                   "There should be more than 1000 bytes written, but there was only " + result.size());
    }
}