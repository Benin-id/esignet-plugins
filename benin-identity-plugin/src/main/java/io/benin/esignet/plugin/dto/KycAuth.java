package io.peru.esignet.plugin.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@AllArgsConstructor
public class KycAuth implements Serializable {

    private String kycToken;
    private String partnerSpecificUserToken;
    private LocalDateTime responseTime;
    private String transactionId;
    private String individualId;
    private DatosPersona datosPersona;
}