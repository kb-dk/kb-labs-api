package dk.kb.webservice.exception;

import javax.ws.rs.core.Response;

/*
 * Superclass for Exceptions that has a specific HTTP response code.
 * </p><p>
 * Note that this class has 2 "modes": Plain text message or custom response object,
 * intended for use with OpenAPI-generated Dto response objects.
 */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 27182819L;
    private final Response.Status responseStatus;

    private String mimeType = "text/plain";
    private Object entity = null;

	public Response.Status getResponseStatus() {
		return responseStatus;
	}
	
	public ServiceException(Response.Status responseStatus) {
        super();
		this.responseStatus = responseStatus;
	}
    
    public ServiceException(String message, Response.Status responseStatus) {
        super(message);
		this.responseStatus = responseStatus;
	}
    
    public ServiceException(String message, Throwable cause, Response.Status responseStatus) {
        super(message, cause);
		this.responseStatus = responseStatus;
	}
    
    public ServiceException(Throwable cause, Response.Status responseStatus) {
        super(cause);
		this.responseStatus = responseStatus;
	}

	/**
	 * Custom message object.
	 * @param mimeType the MIME type used for the HTTP response headers.
	 * @param entity the entity to translate into the HTTP response body (normally an OpenAPI generated Dto).
	 * @param responseStatus HTTP response code.
	 */
	public ServiceException(String mimeType, Object entity, Response.Status responseStatus) {
        super();
		this.responseStatus = responseStatus;
		this.mimeType = mimeType;
		this.entity = entity;
	}

	/**
	 * Custom message object.
	 * @param mimeType the MIME type used for the HTTP response headers.
	 * @param entity the entity to translate into the HTTP response body (normally an OpenAPI generated Dto).
	 * @param cause the originating Exception.
	 * @param responseStatus HTTP response code.
	 */
	public ServiceException(String mimeType, Object entity, Throwable cause, Response.Status responseStatus) {
        super(cause);
		this.responseStatus = responseStatus;
		this.mimeType = mimeType;
		this.entity = entity;
	}

	public String getMimeType() {
		return mimeType;
	}

	public Object getEntity() {
		return entity == null ? getMessage() : entity;
	}
}
