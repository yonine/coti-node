package io.coti.trustscore.http;

import io.coti.basenode.http.BaseResponse;
import io.coti.trustscore.data.UserTrustScoreData;
import io.coti.trustscore.data.tsbuckets.BucketData;
import lombok.Data;

import java.util.List;

@Data
public class GetUserTrustScoreComponentsResponse extends BaseResponse {
    private UserTrustScoreData userTrustScoreData;
    private List<BucketData> bucketEventDataList;

    public GetUserTrustScoreComponentsResponse(UserTrustScoreData userTrustScoreData, List<BucketData> bucketEventDataListResponseData) {
        this.userTrustScoreData = userTrustScoreData;
        this.bucketEventDataList = bucketEventDataListResponseData;
    }

}