package io.benin.esignet.plugin.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KycAuth implements Serializable {
    private static final long serialVersionUID = 1L;

    private String kycToken;
    private LocalDateTime timestamp;
    private String transactionId;
    private String individualId;
    private String partnerSpecificUserToken;
    private List<OtpVerifiedUser> userDataList;
}
