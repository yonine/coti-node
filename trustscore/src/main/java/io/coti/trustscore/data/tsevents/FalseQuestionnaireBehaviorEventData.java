package io.coti.trustscore.data.tsevents;


import io.coti.trustscore.data.tsenums.FinalEventType;
import io.coti.trustscore.data.tsenums.UserType;
import io.coti.trustscore.data.parameters.UserParameters;
import io.coti.trustscore.http.InsertEventScoreRequest;
import lombok.Data;

import java.util.Map;

@Data
public class FalseQuestionnaireBehaviorEventData extends BehaviorEventData {

    private static final long serialVersionUID = 3576212918648240934L;
    public static Map<UserType, UserParameters> userParametersMap;

    public FalseQuestionnaireBehaviorEventData() {
    }

    public FalseQuestionnaireBehaviorEventData(InsertEventScoreRequest request) {
        super(request);
    }
}