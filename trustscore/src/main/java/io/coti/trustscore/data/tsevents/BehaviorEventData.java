package io.coti.trustscore.data.tsevents;


import io.coti.trustscore.data.tsenums.FinalEventType;
import io.coti.trustscore.http.InsertEventScoreRequest;
import lombok.Data;

@Data
public abstract class BehaviorEventData extends SignedEventData {

    private static final long serialVersionUID = 5474716203264386699L;

    public BehaviorEventData() {
    }

    public BehaviorEventData(InsertEventScoreRequest request) {
        super(request, request.eventIdentifier);
    }
}