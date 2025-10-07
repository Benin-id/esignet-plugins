/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.benin.esignet.plugin.service;

import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.Authenticator;
import io.benin.esignet.plugin.dto.KycAuth;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.kernel.keymanagerservice.dto.AllCertificatesDataResponseDto;
import io.mosip.kernel.keymanagerservice.dto.CertificateDataResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.*;
import io.benin.esignet.plugin.dto.OtpRequest;
import io.benin.esignet.plugin.dto.OtpResponse;

import io.benin.esignet.plugin.util.OtpAPIClient;

@ConditionalOnProperty(value = "mosip.esignet.integration.authenticator", havingValue = "BeninAuthenticationService")
@Component
@Slf4j
public class BeninAuthenticationService implements Authenticator {

    private static final String APPLICATION_ID = "MOCK_AUTHENTICATION_SERVICE";

    @Autowired
    private KeymanagerService keymanagerService;

    @Autowired
    private HelperService helperService;

    @Autowired
    private CacheService cacheService;

    @Autowired
   private OtpAPIClient  OtpAPIClient;

    @Validated
    @Override
    public KycAuthResult doKycAuth(@NotBlank String relyingPartyId, @NotBlank String clientId,
                                   @NotNull @Valid KycAuthDto kycAuthDto) throws KycAuthException {

        log.info("Started to build kyc-auth request with transactionId : {} && clientId : {}",
                kycAuthDto.getTransactionId(), clientId);

        KycAuthResult kycAuthResult=null;
        for (AuthChallenge authChallenge : kycAuthDto.getChallengeList()) {
            switch (authChallenge.getAuthFactorType()) {
//                case "KBA":
//                    kycAuthResult = helperService.validateKnowledgeBasedAuth(kycAuthDto.getIndividualId(), authChallenge);
//                    break;
                case "OTP":
                    kycAuthResult = helperService.validateOtpBasedAuth(kycAuthDto.getIndividualId(), authChallenge);
                    break;
//                case "WLA":
//                    kycAuthResult = helperService.validateWla(kycAuthDto.getIndividualId(),authChallenge);
//                    break;
                default:
                    throw new KycAuthException("invalid_auth_challenge");
            }
        }
        return  kycAuthResult;
    }

    @Override
    public KycExchangeResult doKycExchange(String relyingPartyId, String clientId, KycExchangeDto kycExchangeDto)
            throws KycExchangeException {
        log.info("Started to build kyc-exchange request with transactionId : {} && clientId : {}",
                kycExchangeDto.getTransactionId(), clientId);
        try {
            KycAuth result = cacheService.getKycAuth(kycExchangeDto.getKycToken());
            if(result==null ){
                throw new KycExchangeException("peru-ida-006");
            }
            try {
                Map<String, Object> kyc = helperService.buildKycDataBasedOnPolicy(kycExchangeDto.getAcceptedClaims(),
                        result.getUserDataList());
                kyc.put("sub", result.getPartnerSpecificUserToken());

                String finalKyc= helperService.signKyc(kyc);
                KycExchangeResult kycExchangeResult = new KycExchangeResult();
                kycExchangeResult.setEncryptedKyc(finalKyc);
                return kycExchangeResult;
            } catch (Exception ex) {
                log.error("Failed to build kyc data", ex);
                throw new KycExchangeException("mock-ida-008");
            }

        } catch (KycExchangeException e) {
            throw e;
        } catch (Exception e) {
            log.error("IDA Kyc-exchange failed with clientId : {}", clientId, e);
        }
        throw new KycExchangeException("peru-ida-005", "Failed to build kyc data");
    }

    @Override
    public SendOtpResult sendOtp(String relyingPartyId, String clientId, SendOtpDto sendOtpDto)
            throws SendOtpException {
        if (sendOtpDto == null) {
            throw new SendOtpException("invalid_request");
        }

        String transactionId = sendOtpDto.getTransactionId();
        if (StringUtils.isEmpty(transactionId)) {
            throw new SendOtpException("invalid_transaction_id");
        }

        // Extract additional info
       Map<String, String> additionalInfo = sendOtpDto.getAdditionalInfo();
        if (additionalInfo == null || additionalInfo.isEmpty()) {
            throw new SendOtpException("missing_additional_info");
        }

        String countryCode = additionalInfo.get("code");
        String mobile = additionalInfo.get("mobile");

        String npi = sendOtpDto.getIndividualId();

        // Validate phone credentials
        if (StringUtils.isEmpty(countryCode) || StringUtils.isEmpty(mobile)) {
            throw new SendOtpException("invalid_phone_credentials");
        }
        // Validate NPI
        if (StringUtils.isEmpty(npi)) {
            throw new SendOtpException("invalid_individual_id");
        }

        SendOtpResult sendOtpResult = new SendOtpResult();

           OtpRequest otpRequest = new OtpRequest();
            otpRequest.setNPI(npi);
            otpRequest.setSEND("1"); // 1 = send via SMS
            otpRequest.setINDICATIF_TELEPHONE(countryCode);
            otpRequest.setNUMERO_TELEPHONE(mobile);

        OtpResponse apiResponse;
        try {
            apiResponse = OtpAPIClient.sendOtpRequest(otpRequest);

            if (apiResponse == null) {
                throw new SendOtpException("api_error");
            }
            if(apiResponse.getEtat().equals("0")){
                throw new SendOtpException("invalid_npi");
            }
             if(apiResponse.getEtat().equals("2")){
                 throw new SendOtpException("invalid_phone_number");
             }
            sendOtpResult.setMaskedEmail(apiResponse.getEtat());   // from STATUT
            sendOtpResult.setMaskedMobile(apiResponse.getMessage());
            System.out.println("apiResponse: " + apiResponse);
//            logger.info("OTP sent successfully. Transaction ID: {}, Response: {}",
//                    transactionId, apiResponse);
        } catch (SendOtpException  e) {
            throw e;
        }catch (Exception e) {
//            logger.error("Failed to send OTP. Transaction ID: {}", transactionId, e);
            throw new SendOtpException("otp_send_failed");
        }

        sendOtpResult.setTransactionId(sendOtpDto.getTransactionId());
        return sendOtpResult;
    }

    @Override
    public boolean isSupportedOtpChannel(String channel) {
        return helperService.isSupportedOtpChannel(channel);
    }

    @Override
    public List<KycSigningCertificateData> getAllKycSigningCertificates() {
        List<KycSigningCertificateData> certs = new ArrayList<>();
        AllCertificatesDataResponseDto allCertificatesDataResponseDto = keymanagerService.getAllCertificates(APPLICATION_ID,
                Optional.empty());
        for (CertificateDataResponseDto dto : allCertificatesDataResponseDto.getAllCertificates()) {
            certs.add(new KycSigningCertificateData(dto.getKeyId(), dto.getCertificateData(),
                    dto.getExpiryAt(), dto.getIssuedAt()));
        }
        return certs;
    }
}