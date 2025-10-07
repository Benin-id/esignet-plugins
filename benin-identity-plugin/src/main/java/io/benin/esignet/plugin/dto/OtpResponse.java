package io.benin.esignet.plugin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpResponse {
    @JsonProperty("etat")
    private String etat;

    @JsonProperty("message")
    private String message;

    // Getters and Setters
    public String getEtat() { return etat; }
    public void setEtat(String etat) { this.etat = etat; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}