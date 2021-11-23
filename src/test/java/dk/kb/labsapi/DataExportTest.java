package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.webservice.exception.ForbiddenServiceException;
import dk.kb.webservice.exception.InvalidArgumentServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        final String OKAY = "doms_aviser_edition:uuid:15e8ea25-a194-4f23-bcae-a938c0292611"; // 1829
        final String NOGO = "doms_aviser_edition:uuid:69b4d5c4-ac19-4fea-837b-688c7ba0178a"; // 1963

        String downloadOK = DataExport.getInstance().resolveTicket(OKAY);
        assertFalse(downloadOK.isEmpty(), "Some ticket should be returned for edition '" + OKAY + "'");
        log.info("Got ticket '" + downloadOK + "' for OKAY '" + OKAY + "'");

        assertThrows(ForbiddenServiceException.class, () -> {
            DataExport.getInstance().resolveTicket(NOGO);
        }, "Requesting ticket for NOGO '" + NOGO + "' should fail");
    }
}