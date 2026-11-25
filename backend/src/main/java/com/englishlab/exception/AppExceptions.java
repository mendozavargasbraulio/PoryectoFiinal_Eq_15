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

    /** Token de verificación de correo inválido, expirado o ya usado -> HTTP 400 con {@code code}. */
    public static class EmailVerificationException extends RuntimeException {

        public enum Reason { INVALID, EXPIRED, USED }

        private final Reason reason;

        public EmailVerificationException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    /** Login con credenciales correctas pero correo sin verificar -> HTTP 403 con code EMAIL_NOT_VERIFIED. */
    public static class EmailNotVerifiedException extends RuntimeException {

        public EmailNotVerifiedException(String message) {
            super(message);
        }
    }

    /** Google Sign-In sin configurar (falta app.google.client-id) -> HTTP 503. */
    public static class GoogleAuthUnavailableException extends RuntimeException {

        public GoogleAuthUnavailableException(String message) {
            super(message);
        }
    }

    /** Error de un campo concreto (array `fieldErrors` de la respuesta de validación). */
    public record ApiFieldError(String field, String message) {}
}
