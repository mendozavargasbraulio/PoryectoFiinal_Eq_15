package com.englishlab.service;

import com.englishlab.config.AppConfig.TwilioVerifyProperties;
import com.englishlab.exception.AppExceptions.SmsVerificationUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Integración ligera con Twilio Verify API (SMS OTP) usando el {@link RestClient} de Spring.
 * Si {@code twilio.verify.enabled=false} o faltan credenciales, no rompe el arranque y lanza
 * un error controlado al intentar enviar o comprobar un código.
 */
@Service
public class TwilioVerifyService {

    private static final Logger log = LoggerFactory.getLogger(TwilioVerifyService.class);
    private static final String VERIFY_API_BASE_URL = "https://verify.twilio.com";

    private final TwilioVerifyProperties props;
    private final RestClient restClient;

    @Autowired
    public TwilioVerifyService(TwilioVerifyProperties props, RestClient.Builder restClientBuilder) {
        this(props, buildRestClient(restClientBuilder));
    }

    TwilioVerifyService(TwilioVerifyProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.baseUrl(VERIFY_API_BASE_URL).requestFactory(factory).build();
    }

    /** Verifica que la recuperación por SMS esté habilitada y configurada antes de usarla. */
    public void ensureConfigured() {
        if (!props.enabled() || isBlank(props.accountSid()) || isBlank(props.authToken()) || isBlank(props.serviceSid())) {
            throw new SmsVerificationUnavailableException(
                    "La recuperación de contraseña por SMS no está disponible en este momento.");
        }
    }

    /** Envía un código OTP por SMS mediante Twilio Verify. */
    public void sendSmsCode(String phoneE164) {
        ensureConfigured();
        String accountSid = props.accountSid().trim();
        String authToken = props.authToken().trim();
        String serviceSid = props.serviceSid().trim();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneE164);
        form.add("Channel", "sms");

        try {
            restClient.post()
                    .uri("/v2/Services/{serviceSid}/Verifications", serviceSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Código SMS enviado vía Twilio Verify a {}", maskPhone(phoneE164));
        } catch (RestClientResponseException ex) {
            log.warn("Twilio Verify rechazó envío SMS a {}: HTTP {}", maskPhone(phoneE164), ex.getStatusCode().value());
            throw new SmsVerificationUnavailableException("No se pudo enviar el código SMS. Intenta más tarde.");
        } catch (Exception ex) {
            log.warn("Error de comunicación con Twilio Verify al enviar SMS a {}: {}", maskPhone(phoneE164), ex.getMessage());
            throw new SmsVerificationUnavailableException("No se pudo enviar el código SMS. Intenta más tarde.");
        }
    }

    /**
     * Comprueba un código OTP contra Twilio Verify. Devuelve {@code true} únicamente si Twilio
     * responde {@code status == "approved"} y {@code valid == true}.
     */
    public boolean checkSmsCode(String phoneE164, String code) {
        ensureConfigured();
        String accountSid = props.accountSid().trim();
        String authToken = props.authToken().trim();
        String serviceSid = props.serviceSid().trim();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneE164);
        form.add("Code", code);

        try {
            VerificationCheckResponse response = restClient.post()
                    .uri("/v2/Services/{serviceSid}/VerificationCheck", serviceSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(VerificationCheckResponse.class);
            return response != null
                    && "approved".equalsIgnoreCase(response.status())
                    && Boolean.TRUE.equals(response.valid());
        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            // 404 (código expirado/ya consumido) o 400 (formato/código inválido) -> verificación rechazada.
            if (statusCode == 400 || statusCode == 404) {
                log.info("Twilio Verify rechazó código SMS para {}: HTTP {}", maskPhone(phoneE164), statusCode);
                return false;
            }
            log.warn("Error consultando Twilio Verify para {}: HTTP {}", maskPhone(phoneE164), statusCode);
            throw new SmsVerificationUnavailableException("No se pudo verificar el código SMS. Intenta más tarde.");
        } catch (Exception ex) {
            log.warn("Error de comunicación con Twilio Verify al verificar código para {}: {}", maskPhone(phoneE164), ex.getMessage());
            throw new SmsVerificationUnavailableException("No se pudo verificar el código SMS. Intenta más tarde.");
        }
    }

    private static String maskPhone(String phone) {
        if (isBlank(phone) || phone.length() <= 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerificationCheckResponse(
            @JsonProperty("status") String status,
            @JsonProperty("valid") Boolean valid
    ) {}
}
