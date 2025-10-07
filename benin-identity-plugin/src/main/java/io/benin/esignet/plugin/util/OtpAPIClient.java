package io.benin.esignet.plugin.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.benin.esignet.plugin.dto.OtpRequest;
import io.benin.esignet.plugin.dto.OtpResponse;
import io.benin.esignet.plugin.dto.OtpVerifyRequest;
import io.benin.esignet.plugin.dto.OtpVerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Client utility for sending OTP requests and verifying OTPs via external service.
 */
@Component
public class OtpAPIClient {

    private static final Logger logger = LoggerFactory.getLogger(OtpAPIClient.class);

    @Value("${anipbj.otp.endpoint}")
    private String otpEndpoint;

    @Value("${anipbj.otp.verify-endpoint}")
    private String otpVerifyEndpoint;

    @Value("${anipbj.otp.login}")
    private String loginHeader;

    @Value("${anipbj.otp.apiKey}")
    private String apiKeyHeader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends OTP request to the configured endpoint.
     */
    public OtpResponse sendOtpRequest(OtpRequest request) throws Exception {
        return executePostRequest(otpEndpoint, request, OtpResponse.class, "Send OTP");
    }

    /**
     * Verifies OTP with the configured endpoint.
     */
    public OtpVerifyResponse verifyOtpRequest(OtpVerifyRequest request) throws Exception {
        return executePostRequest(otpVerifyEndpoint, request, OtpVerifyResponse.class, "Verify OTP");
    }

    /**
     * Generic POST executor for JSON APIs.
     */
    private <T> T executePostRequest(String endpoint, Object request, Class<T> responseType, String actionName) throws Exception {
        long startTime = System.currentTimeMillis();
        HttpURLConnection connection = null;

        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();

            // Configure request
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Login", loginHeader);
            connection.setRequestProperty("apiKey", apiKeyHeader);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);

            // Serialize request
            String jsonInput = objectMapper.writeValueAsString(request);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
            }

            // Handle response
            int responseCode = connection.getResponseCode();
            StringBuilder response = new StringBuilder();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode == HttpURLConnection.HTTP_OK
                                    ? connection.getInputStream()
                                    : connection.getErrorStream(),
                            StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return objectMapper.readValue(response.toString(), responseType);
            } else {
                throw new RuntimeException(actionName + " failed. HTTP " + responseCode + " - " + response);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            logger.debug("{} API call completed in {} ms", actionName, System.currentTimeMillis() - startTime);
        }
    }
}
