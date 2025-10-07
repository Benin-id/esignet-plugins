package io.benin.esignet.plugin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpVerifyRequest {

    @JsonProperty("NPI")
    private String npi;

    @JsonProperty("OTP")
    private String otp;

    // Getters and Setters
    public String getNpi() {
        return npi;
    }

    public void setNpi(String npi) {
        this.npi = npi;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
