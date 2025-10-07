package io.benin.esignet.plugin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;


public class OtpVerifyResponse {

    @JsonProperty("etat")
    private String etat;

    @JsonProperty("message")
    private String message;

    @JsonProperty("Data")
    private List<OtpVerifiedUser> data;

    // Getters and Setters
    public String getEtat() {
        return etat;
    }

    public void setEtat(String etat) {
        this.etat = etat;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<OtpVerifiedUser> getData() {
        return data;
    }

    public void setData(List<OtpVerifiedUser> data) {
        this.data = data;
    }

//    @Override
//    public String toString() {
//        return "OtpVerifyResponse{" +
//                "status='" + status + '\'' +
//                ", message='" + message + '\'' +
//                ", data=" + data +
//                '}';
//    }

}
