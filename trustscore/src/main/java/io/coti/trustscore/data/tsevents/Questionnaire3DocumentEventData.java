package io.coti.trustscore.data.tsevents;

import io.coti.trustscore.data.tsenums.FinalEventType;
import io.coti.trustscore.http.InsertDocumentScoreRequest;
import lombok.Data;

@Data
public class Questionnaire3DocumentEventData extends DocumentEventData {

    private static final long serialVersionUID = 2750243157421392107L;

    public Questionnaire3DocumentEventData() {
    }

    public Questionnaire3DocumentEventData(InsertDocumentScoreRequest request) {
        super(request);
        this.score = request.getScore();
    }

}
