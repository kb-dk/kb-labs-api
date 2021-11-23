package dk.kb.webservice.exception;

import javax.ws.rs.core.Response;

/*
 * Custom web-exception class (403)
 */
public class ForbiddenServiceException extends ServiceException {

    //Constant fields for the OpenApi
    public static final String description = "ForbiddenServiceException";
    public static final String responseCode = "403";

    private static final long serialVersionUID = 27182673L;
    private static final Response.Status responseStatus = Response.Status.FORBIDDEN; // 403

    public ForbiddenServiceException() {
        super(responseStatus);
    }

    public ForbiddenServiceException(String message) {
        super(message, responseStatus);
    }

    public ForbiddenServiceException(String message, Throwable cause) {
        super(message, cause, responseStatus);
    }

    public ForbiddenServiceException(Throwable cause) {
        super(cause, responseStatus);
    }

    public ForbiddenServiceException(String mimeType, Object entity) {
        super(mimeType, entity, responseStatus);
    }

    public ForbiddenServiceException(String mimeType, Object entity, Throwable cause) {
        super(mimeType, entity, cause, responseStatus);
    }

}
