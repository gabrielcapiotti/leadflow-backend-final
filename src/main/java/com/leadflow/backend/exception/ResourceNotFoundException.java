package com.leadflow.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception lançada quando um recurso solicitado (Lead, Vendor, etc)
 * não é encontrado no banco de dados ou acesso é negado.
 * 
 * Retorna HTTP 404 NOT_FOUND conforme RFC 7231
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
