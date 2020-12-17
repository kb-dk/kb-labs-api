package dk.kb.webservice.exception;

import javax.ws.rs.core.Response;

/*
 * Custom web-exception class (500)
 */
public class InternalServiceException extends ServiceException {
    
    //Constant fields for the OpenApi
    public static final String description = "InternalServiceException";
    public static final String responseCode = "500";
    
    
    private static final long serialVersionUID = 27182820L;
    private static final Response.Status responseStatus = Response.Status.INTERNAL_SERVER_ERROR; //500
    
    public InternalServiceException() {
        super(responseStatus);
    }
    
    public InternalServiceException(String message) {
        super(message, responseStatus);
    }
    
    public InternalServiceException(String message, Throwable cause) {
        super(message, cause, responseStatus);
    }
    
    public InternalServiceException(Throwable cause) {
        super(cause, responseStatus);
    }
    
    public InternalServiceException(String mimeType, Object entity) {
        super(mimeType, entity, responseStatus);
    }

    public InternalServiceException(String mimeType, Object entity, Throwable cause) {
        super(mimeType, entity, cause, responseStatus);
    }

}


