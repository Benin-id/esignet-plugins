package io.benin.esignet.plugin.dto;

public class SendOtpException extends Exception {
    private final String errorCode;

    public SendOtpException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public SendOtpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}