package io.benin.esignet.plugin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.benin.esignet.plugin.dto.DatosPersona;
import io.benin.esignet.plugin.dto.OtpVerifiedUser;
import io.benin.esignet.plugin.dto.OtpVerifyRequest;
import io.benin.esignet.plugin.dto.OtpVerifyResponse;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.spi.KeyBindingValidator;
import io.mosip.esignet.api.util.ErrorConstants;
import io.benin.esignet.plugin.dto.KycAuth;
//import io.peru.esignet.plugin.dto.DatosPersona;

//import io.peru.esignet.plugin.dto.Envelope;
import io.benin.esignet.plugin.util.IdentityAPIClient;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import io.benin.esignet.plugin.util.OtpAPIClient;
import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class HelperService {

    public static final String ALGO_SHA3_256 = "SHA3-256";

    private final String FIELD_ID_KEY="id";

    private static final Base64.Encoder urlSafeEncoder = Base64.getUrlEncoder().withoutPadding();

    public static final String APPLICATION_ID = "OIDC_SERVICE";

    @Value("${mosip.esignet.peru.authenticator.otp-channels:email,phone}")
    private List<String> otpChannels;

    @Value("${mosip.esignet.peru.authenticator.otp-value:111111}")
    private String otpValue;

    @Value("#{${mosip.esignet.peru.authenticator.auth-factor.kba.field-details}}")
    private List<Map<String,String>> fieldDetailList;

    @Value("${mosip.esignet.peru.authenticator.auth-factor.kba.individual-id-field}")
    private String idField;
//    @Value("${mosip.esignet.cache.security.algorithm-name}")
//    private String aesECBTransformation;

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private IdentityAPIClient identityAPIClient;
    @Autowired
    private OtpAPIClient  OtpAPIClient;

    @Autowired
    private KeyBindingValidator keyBindingValidator;

    @PostConstruct
    public void initialize() throws KycAuthException {
        log.info("Started to setup Peru Authenticator");
        boolean individualIdFieldIsValid = false;
        if(fieldDetailList==null || fieldDetailList.isEmpty()){
            log.error("Invalid configuration for field-details");
            throw new KycAuthException("Peru authenticator field is not configured properly");
        }
        for (Map<String, String> field : fieldDetailList) {
            if (field.containsKey(FIELD_ID_KEY) && field.get(FIELD_ID_KEY).equals(idField)) {
                individualIdFieldIsValid = true;
                break;
            }
        }
        if (!individualIdFieldIsValid) {
            log.error("Invalid configuration: The 'individual-id-field' '{}' is not available in 'field-details'.", idField);
            throw new KycAuthException("Invalid configuration: individual-id-field is not available in field-details.");
        }
    }

    public static String b64Encode(String value) {
        return urlSafeEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isSupportedOtpChannel(String channel) {
        return channel != null && otpChannels.contains(channel.toLowerCase());
    }

    protected static LocalDateTime getUTCDateTime() {
        return ZonedDateTime
                .now(ZoneOffset.UTC).toLocalDateTime();
    }

    public KycAuthResult validateOtpBasedAuth(String individualId, AuthChallenge authChallenge) throws KycAuthException {
        // Validate input parameters
        if (authChallenge == null) {
            log.error("AuthChallenge is null for individualId: {}", individualId);
            throw new KycAuthException(ErrorConstants.AUTH_FAILED);
        }

        if (!"OTP".equals(authChallenge.getAuthFactorType())) {
            log.error("Unsupported auth factor type: {}", authChallenge.getAuthFactorType());
            throw new KycAuthException(ErrorConstants.AUTH_FAILED);
        }


        try {

                // Build the request object for OTP verification
                OtpVerifyRequest verifyRequest = new OtpVerifyRequest();
                verifyRequest.setNpi(individualId);
                verifyRequest.setOtp(authChallenge.getChallenge());

                // Call OTP Verify API
               OtpVerifyResponse response = OtpAPIClient.verifyOtpRequest(verifyRequest);
            if (response == null) {
                log.error("Null response from OTP verification API for individualId: {}", individualId);
                throw new KycAuthException(ErrorConstants.AUTH_FAILED);
            }
            if(response.getEtat().equals("2")){
                throw new KycAuthException(ErrorConstants.AUTH_FAILED);
            }

                    System.out.println("apiResponse: " + response.getData());
                    List<OtpVerifiedUser> userDataList = response.getData();
            if (userDataList == null || userDataList.isEmpty()) {
                log.error("No user data in OTP verification response for individualId: {}", individualId);
                throw new KycAuthException(ErrorConstants.AUTH_FAILED);
            }
//                    // Generate KYC token
                    String TOKEN_CONNEXION = userDataList.get(0).getTokenConnexion();
                    System.out.println("userDataList: " + userDataList);
                   String kycToken = generateB64EncodedHash(ALGO_SHA3_256, UUID.randomUUID().toString());

                    KycAuthResult kycAuthResult = new KycAuthResult();
                    kycAuthResult.setKycToken(kycToken);
                    kycAuthResult.setPartnerSpecificUserToken(TOKEN_CONNEXION);

                    cacheService.setKycAuth(kycToken, new KycAuth(
                            kycToken,
                            LocalDateTime.now(ZoneOffset.UTC),
                            "transactionId",
                            individualId,
                            TOKEN_CONNEXION, // This is now the partnerSpecificUserToken parameter
                            userDataList
                    ));
            log.info("OTP authentication successful for individualId: {}", individualId);
            return kycAuthResult;

        }  catch (KycAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate OTP authentication for individualId: {}", individualId, e);
            throw new KycAuthException(ErrorConstants.AUTH_FAILED);
        }
    }

//    public KycAuthResult validateKnowledgeBasedAuth(String individualId, AuthChallenge authChallenge) throws KycAuthException {
//
//        KycAuthResult  kycAuthResult= new KycAuthResult();
//
//        try {
//            Envelope envelope = identityAPIClient.getIdentity(individualId);
//
//            if(envelope!=null && envelope.getBody()!=null &&
//                    envelope.getBody().getConsultarResponse()!=null &&
//                    envelope.getBody().getConsultarResponse().getResponseReturn()!=null &&
//                    envelope.getBody().getConsultarResponse().getResponseReturn().getDatosPersona()!=null) {
//
//                DatosPersona datosPersona=envelope.getBody().getConsultarResponse().getResponseReturn().getDatosPersona();
//                boolean authStatus=verifyKnowledgeBasedChallenge(authChallenge.getChallenge(),datosPersona);
//                if(authStatus){
//                    String kycToken = generateB64EncodedHash(ALGO_SHA3_256, UUID.randomUUID().toString());
//                    kycAuthResult.setKycToken(kycToken);
//                    kycAuthResult.setPartnerSpecificUserToken(individualId);
//                    cacheService.setKycAuth(kycToken,new KycAuth(kycToken, individualId, LocalDateTime.now(ZoneOffset.UTC), "transactionId",
//                            individualId
//                            ,datosPersona
//                    ));
//                    return kycAuthResult;
//                }
//            }
//        } catch (Exception e) {
//            log.error("Failed to do the Authentication",e);
//            throw new KycAuthException(ErrorConstants.AUTH_FAILED );
//        }
//        throw new KycAuthException(ErrorConstants.AUTH_FAILED );
//    }

//    public KycAuthResult validateWla(String individualId, AuthChallenge authChallenge) throws KycAuthException {
//        KycAuthResult  kycAuthResult= new KycAuthResult();
//
//        try {
//
//            BindingAuthResult bindingAuthResult = keyBindingValidator.validateBindingAuth("transactionId",
//                    individualId, List.of(authChallenge));
//            if(bindingAuthResult == null)
//                throw new KycAuthException(ErrorConstants.AUTH_FAILED );
//
//            Envelope envelope = identityAPIClient.getIdentity(individualId);
//
//            if(envelope!=null && envelope.getBody()!=null &&
//                    envelope.getBody().getConsultarResponse()!=null &&
//                    envelope.getBody().getConsultarResponse().getResponseReturn()!=null &&
//                    envelope.getBody().getConsultarResponse().getResponseReturn().getDatosPersona()!=null) {
//                DatosPersona datosPersona=envelope.getBody().getConsultarResponse().getResponseReturn().getDatosPersona();
//                String kycToken = generateB64EncodedHash(ALGO_SHA3_256, UUID.randomUUID().toString());
//                    kycAuthResult.setKycToken(kycToken);
//                    kycAuthResult.setPartnerSpecificUserToken(individualId);
//                    cacheService.setKycAuth(kycToken,new KycAuth(kycToken, individualId, LocalDateTime.now(ZoneOffset.UTC), "transactionId",
//                            individualId
//                            ,datosPersona
//                    ));
//                    return kycAuthResult;
//            }
//        } catch (Exception e) {
//            log.error("Failed to do the Authentication ",e);
//            throw new KycAuthException(ErrorConstants.AUTH_FAILED );
//        }
//        throw new KycAuthException(ErrorConstants.AUTH_FAILED );
//    }

//    private boolean verifyKnowledgeBasedChallenge(String encodedChallenge,DatosPersona datosPersona) throws KycAuthException {
//        if(CollectionUtils.isEmpty(fieldDetailList)){
//            log.error("KBA field details not configured");
//            throw new KycAuthException(ErrorConstants.AUTH_FAILED);
//        }
//        try{
//            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedChallenge);
//            String challenge = new String(decodedBytes, StandardCharsets.UTF_8);
//            Map<String, String> challengeMap = objectMapper.readValue(challenge, Map.class);
//
//            for(Map<String,String> fieldDetail:fieldDetailList){
//                if(challengeMap.containsKey(fieldDetail.get(FIELD_ID_KEY))) {
//                    String challengeField = fieldDetail.get(FIELD_ID_KEY);
//                    String challengeValue = challengeMap.get(challengeField);
//                    String identityDataValue = getIdentityDataFieldValue(datosPersona, challengeField);
//
//                    if(fieldDetail.get("type").equals("date")) {
//                        LocalDate inputDate = LocalDate.parse(challengeValue);
//                        LocalDate actualDate = LocalDate.parse(identityDataValue, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
//
//                        if(!actualDate.isEqual(inputDate))
//                            return false;
//                    }
//                    else if(!identityDataValue.equals(challengeValue)) {
//                        return false;
//                    }
//                }
//            }
//        }catch (Exception e){
//            log.error("Failed to decode KBA challenge or compare it with IdentityData", e);
//            throw new KycAuthException(ErrorConstants.AUTH_FAILED);
//        }
//        return true;
//    }



//    private String encryptData(String data) {
//        try {
//            Cipher cipher = Cipher.getInstance(aesECBTransformation);
//            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
//            cipher.init(Cipher.ENCRYPT_MODE, getSecretKeyFromHSM());
//            return IdentityProviderUtil.b64Encode(cipher.doFinal(dataBytes, 0, dataBytes.length));
//        } catch(Exception e) {
//            log.error("Error encrypting data", e);
//            throw new Exception(ErrorConstants.AES_CIPHER_FAILED);
//        }
//    }

    private String getIdentityDataFieldValue(DatosPersona datosPersona, String challengeField) throws Exception {
        Field field = datosPersona.getClass().getDeclaredField(challengeField);
        field.setAccessible(true);
        Object fieldValue = field.get(datosPersona);
        return (String) fieldValue;
    }

    private String generateB64EncodedHash(String algorithm, String value) throws KycAuthException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return urlSafeEncoder.encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Invalid algorithm : {}", algorithm, ex);
            throw new KycAuthException("invalid_algorithm");
        }
    }

    public Map<String, Object> buildKycDataBasedOnPolicy(List<String> claims,List<OtpVerifiedUser> userDataList ) throws KycExchangeException {
        Map<String, Object> kyc = new HashMap<>();
       // Get the first user from the list
        OtpVerifiedUser user = userDataList.get(0);
        for (String claim : claims) {
            switch (claim) {
                case "name":
                    if (user.getNom() != null || user.getPrenoms() != null) {
                        StringBuilder fullNameBuilder = new StringBuilder();
                        if (user.getPrenoms() != null) {
                            fullNameBuilder.append(user.getPrenoms());
                        }
                        if (user.getNom() != null) {
                            if (fullNameBuilder.length() > 0) {
                                fullNameBuilder.append(" ");
                            }
                            fullNameBuilder.append(user.getNom());
                        }
                        kyc.put("name", fullNameBuilder.toString());
                    }
                    break;
                case "address":
                    StringBuilder addressBuilder = new StringBuilder();

//                    if (user.getVillageQuartierResidence() != null) {
//                        addressBuilder.append(user.getVillageQuartierResidence());
//                    }
//
//                    if (user.getArrondissementResidence() != null) {
//                        if (addressBuilder.length() > 0) {
//                            addressBuilder.append(", ");
//                        }
//                        addressBuilder.append(user.getArrondissementResidence());
//                    }

//                    if (user.getCommuneResidence() != null) {
//                        if (addressBuilder.length() > 0) {
//                            addressBuilder.append(", ");
//                        }
//                        addressBuilder.append(user.getCommuneResidence());
//                    }

                    if (user.getDepartementResidence() != null) {
                        if (addressBuilder.length() > 0) {
                            addressBuilder.append(", ");
                        }
                        addressBuilder.append(user.getDepartementResidence());
                    }

                    if (user.getPaysResidence() != null) {
                        if (addressBuilder.length() > 0) {
                            addressBuilder.append(", ");
                        }
                        addressBuilder.append(user.getPaysResidence());
                    }

                    if (addressBuilder.length() > 0) {
                        kyc.put("address", addressBuilder.toString());
                    }
                    break;
                case "gender":
                    if (user.getSexe() != null) {
                        kyc.put("gender", user.getSexe());
                    }
                    break;
                case "given_name":
                    if (user.getNom() != null) {
                        kyc.put("given_name", user.getNom()); // Fixed: removed the boolean check
                    }
                    break;
                case "picture":
                    if (user.getPortrait() != null) {
                        kyc.put("picture","data:image/jpeg;base64,"+ user.getPortrait()); // Fixed: removed the boolean check
                    }
                    break;
                case "birthdate":
                    if (user.getDateDeNaissance() != null) {
                        kyc.put("birthdate", user.getDateDeNaissance());
                    }
                    break;
                case "email":
                    if (user.getMphEmail() != null) {
                        kyc.put("email", user.getMphEmail());
                    }
                    break;
                case "phone_number":
                    if (user.getBjMobilePhoneNumber() != null) {
                        kyc.put("phone_number", user.getBjMobilePhoneNumber());
                    }
                    break;
            }
        }
        return kyc;
    }

    public String signKyc(Map<String, Object> kyc) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(kyc);
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(APPLICATION_ID);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(false);
        jwtSignatureRequestDto.setDataToSign(b64Encode(payload));
        jwtSignatureRequestDto.setIncludeCertHash(false);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

}
