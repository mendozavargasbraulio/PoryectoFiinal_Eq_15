package com.englishlab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.englishlab.config.AppConfig.TwilioVerifyProperties;
import com.englishlab.exception.AppExceptions.SmsVerificationUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TwilioVerifyServiceTest {

    private static final String ACCOUNT_SID = "TEST_ACCOUNT_SID";
    private static final String AUTH_TOKEN = "test-auth-token";
    private static final String SERVICE_SID = "TEST_SERVICE_SID";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("https://verify.twilio.test");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void sendSmsCode_postsVerificationRequestWithBasicAuthAndFormBody() {
        TwilioVerifyService service = buildEnabledService();

        mockServer.expect(requestTo("https://verify.twilio.test/v2/Services/" + SERVICE_SID + "/Verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, containsString("Basic ")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(allOf(
                        containsString("To=%2B529511234567"),
                        containsString("Channel=sms"))))
                .andRespond(withSuccess("{\"sid\":\"VE001\",\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        service.sendSmsCode("+529511234567");

        mockServer.verify();
    }

    @Test
    void checkSmsCode_returnsTrueOnlyWhenApprovedAndValid() {
        TwilioVerifyService service = buildEnabledService();

        mockServer.expect(requestTo("https://verify.twilio.test/v2/Services/" + SERVICE_SID + "/VerificationCheck"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("To=%2B529511234567"),
                        containsString("Code=123456"))))
                .andRespond(withSuccess("{\"status\":\"approved\",\"valid\":true}", MediaType.APPLICATION_JSON));

        assertThat(service.checkSmsCode("+529511234567", "123456")).isTrue();
        mockServer.verify();
    }

    @Test
    void checkSmsCode_returnsFalseWhenNotApprovedOrNotValid() {
        TwilioVerifyService service = buildEnabledService();

        mockServer.expect(requestTo("https://verify.twilio.test/v2/Services/" + SERVICE_SID + "/VerificationCheck"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"pending\",\"valid\":false}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://verify.twilio.test/v2/Services/" + SERVICE_SID + "/VerificationCheck"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"approved\",\"valid\":false}", MediaType.APPLICATION_JSON));

        assertThat(service.checkSmsCode("+529511234567", "000000")).isFalse();
        assertThat(service.checkSmsCode("+529511234567", "111111")).isFalse();
        mockServer.verify();
    }

    @Test
    void checkSmsCode_returnsFalseWhenTwilioResponds404Expired() {
        TwilioVerifyService service = buildEnabledService();

        mockServer.expect(requestTo("https://verify.twilio.test/v2/Services/" + SERVICE_SID + "/VerificationCheck"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound());

        assertThat(service.checkSmsCode("+529511234567", "654321")).isFalse();
        mockServer.verify();
    }

    @Test
    void whenDisabledOrMissingCredentials_throwsControlledUnavailableException() {
        TwilioVerifyService disabled = new TwilioVerifyService(
                new TwilioVerifyProperties(false, ACCOUNT_SID, AUTH_TOKEN, SERVICE_SID),
                restClientBuilder.build());
        TwilioVerifyService missingSid = new TwilioVerifyService(
                new TwilioVerifyProperties(true, ACCOUNT_SID, AUTH_TOKEN, ""),
                restClientBuilder.build());

        assertThatThrownBy(() -> disabled.sendSmsCode("+529511234567"))
                .isInstanceOf(SmsVerificationUnavailableException.class);
        assertThatThrownBy(() -> missingSid.checkSmsCode("+529511234567", "123456"))
                .isInstanceOf(SmsVerificationUnavailableException.class);
        mockServer.verify();
    }

    private TwilioVerifyService buildEnabledService() {
        return new TwilioVerifyService(
                new TwilioVerifyProperties(true, ACCOUNT_SID, AUTH_TOKEN, SERVICE_SID),
                restClientBuilder.build());
    }
}
