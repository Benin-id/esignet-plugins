package io.peru.esignet.plugin.service;

import io.peru.esignet.plugin.dto.KycAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    public static final String KYC_AUTH_CACHE="kycauth";

    @Autowired
    CacheManager cacheManager;

    public void setKycAuth(String kycToken, KycAuth kycAuth) {
        cacheManager.getCache(KYC_AUTH_CACHE).put(kycToken,kycAuth);
    }

    public KycAuth getKycAuth(String kycToken) {
        return cacheManager.getCache(KYC_AUTH_CACHE).get(kycToken, KycAuth.class);	//NOSONAR getCache() will not be returning null here.
    }

}
