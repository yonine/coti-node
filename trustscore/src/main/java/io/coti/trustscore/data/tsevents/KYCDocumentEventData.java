package io.coti.trustscore.data.tsevents;

import io.coti.trustscore.data.tsenums.FinalEventType;
import io.coti.trustscore.http.InsertDocumentScoreRequest;
import io.coti.trustscore.http.SetKycTrustScoreRequest;
import lombok.Data;

@Data
public class KYCDocumentEventData extends DocumentEventData {

    private static final long serialVersionUID = -3207820319956741493L;

    public KYCDocumentEventData() {
    }

    public KYCDocumentEventData(SetKycTrustScoreRequest request) {
        super(request);
        this.score = request.getKycTrustScore() - 10.0;
    }

    public KYCDocumentEventData(InsertDocumentScoreRequest request) {
        super(request);
        this.score = request.getScore() - 10.0;
    }
}
