package dk.kb.labsapi;

import dk.kb.labsapi.model.DocumentDto;
import org.apache.solr.common.SolrDocument;

public class Converter {
    
    public static DocumentDto fromSolrDocument(SolrDocument solrDocument){
        DocumentDto result = new DocumentDto();
        result.setRecordID(solrDocument.getFieldValue("recordID").toString());
        return result;
    }
}
