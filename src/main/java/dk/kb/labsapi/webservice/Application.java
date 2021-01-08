package dk.kb.labsapi.webservice;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import dk.kb.labsapi.api.impl.LabsapiService;
import dk.kb.labsapi.model.DocumentDto;
import dk.kb.webservice.ServiceExceptionMapper;
import org.apache.cxf.jaxrs.provider.StreamingResponseProvider;


public class Application extends javax.ws.rs.core.Application {

    @Override
    public Set<Class<?>> getClasses() {
        return new HashSet<>(Arrays.asList(
                JacksonJsonProvider.class,
                LabsapiService.class,
                ServiceExceptionMapper.class,
                StreamingResponseProvider.class
        ));
    }


}
