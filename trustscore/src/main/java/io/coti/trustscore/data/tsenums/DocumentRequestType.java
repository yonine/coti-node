package io.coti.trustscore.data.tsenums;

import io.coti.trustscore.data.tsevents.KYCDocumentEventData;
import io.coti.trustscore.data.tsevents.Questionnaire1DocumentEventData;
import io.coti.trustscore.data.tsevents.Questionnaire2DocumentEventData;
import io.coti.trustscore.data.tsevents.Questionnaire3DocumentEventData;

public enum DocumentRequestType {
    KYC("KYC", KYCDocumentEventData.class),
    QUESTIONNAIRE1("QUESTIONNAIRE1", Questionnaire1DocumentEventData.class),
    QUESTIONNAIRE2("QUESTIONNAIRE2", Questionnaire2DocumentEventData.class),
    QUESTIONNAIRE3("QUESTIONNAIRE3", Questionnaire3DocumentEventData.class);

    private String text;
    public Class event;

    DocumentRequestType(String text, Class event) {
        this.text = text;
        this.event = event;
    }

    @Override
    public String toString() {
        return text;
    }
}
