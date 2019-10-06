package io.coti.trustscore.data.tsevents;


import io.coti.trustscore.data.tsenums.FinalEventType;
import io.coti.trustscore.http.InsertEventScoreRequest;
import lombok.Data;

@Data
public class InvalidTxBehaviorEventData extends BehaviorEventData {

    private static final long serialVersionUID = -5864778424517102382L;

    public InvalidTxBehaviorEventData() {
    }

    public InvalidTxBehaviorEventData(InsertEventScoreRequest request) {
        super(request);
    }
}