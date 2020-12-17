package dk.kb.webservice;

import dk.kb.webservice.exception.ServiceException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/*
 * Catches {@link ServiceException}s and adjusts the response accordingly.
 */
@Provider
public class ServiceExceptionMapper implements ExceptionMapper<dk.kb.webservice.exception.ServiceException> {

    @Override
    public Response toResponse(ServiceException exception) {
    
        Response.Status responseStatus = exception.getResponseStatus();
        Object entity = exception.getEntity();
        
        return entity != null ?
                Response.status(responseStatus)
                        .entity(entity)
                        //TODO select mimetype more intelligently
                        .type(exception.getMimeType())
                        .build() :
                Response.status(responseStatus).build();
    }
}
