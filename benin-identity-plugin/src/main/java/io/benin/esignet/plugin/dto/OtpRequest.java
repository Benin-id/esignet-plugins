package io.benin.esignet.plugin.dto;

public class OtpRequest {
    private String NPI;
    private String SEND;
    private String INDICATIF_TELEPHONE;
    private String NUMERO_TELEPHONE;

    // Constructors
    public OtpRequest() {}

    // Getters and Setters
    public String getNPI() { return NPI; }
    public void setNPI(String NPI) { this.NPI = NPI; }

    public String getSEND() { return SEND; }
    public void setSEND(String SEND) { this.SEND = SEND; }

    public String getINDICATIF_TELEPHONE() { return INDICATIF_TELEPHONE; }
    public void setINDICATIF_TELEPHONE(String INDICATIF_TELEPHONE) {
        this.INDICATIF_TELEPHONE = INDICATIF_TELEPHONE;
    }

    public String getNUMERO_TELEPHONE() { return NUMERO_TELEPHONE; }
    public void setNUMERO_TELEPHONE(String NUMERO_TELEPHONE) {
        this.NUMERO_TELEPHONE = NUMERO_TELEPHONE;
    }
}