package com.englishlab.exception;

/**
 * Excepciones de la API. Cada una se traduce a un código HTTP en {@link GlobalExceptionHandler}.
 */
public final class AppExceptions {

    private AppExceptions() {
    }

    /** Recurso inexistente -> HTTP 404. */
    public static class NotFoundException extends RuntimeException {

        public NotFoundException(String message) {
            super(message);
        }
    }

    /** Petición con datos inválidos (p. ej. contenido de actividad mal formado) -> HTTP 400. */
    public static class BadRequestException extends RuntimeException {

        public BadRequestException(String message) {
            super(message);
        }
    }

    /** Regla de negocio violada -> HTTP 422. */
    public static class BusinessRuleException extends RuntimeException {

        public BusinessRuleException(String message) {
            super(message);
        }

        public BusinessRuleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Credenciales inválidas en el login -> HTTP 401 (distinto del 401 de la cadena de seguridad). */
    public static class InvalidCredentialsException extends RuntimeException {

        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    /** Error de un campo concreto (array `fieldErrors` de la respuesta de validación). */
    public record ApiFieldError(String field, String message) {}
}
