package io.coti.trustscore.http;

import io.coti.basenode.data.Hash;
import io.coti.basenode.data.SignatureData;
import io.coti.basenode.http.Request;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
public class SetKycTrustScoreRequest extends SignedRequest {
    @NotNull
    private String userType;
    @NotNull
    private double kycTrustScore;
}