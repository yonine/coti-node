package io.coti.trustscore.data.tsenums;

import io.coti.trustscore.data.tsevents.*;

public enum EventRequestType {
    FALSEQUESTIONNAIRE("FALSEQUESTIONNAIRE", FalseQuestionnaireBehaviorEventData.class),
    DOUBLESPENDING("DOUBLESPENDING", DoubleSpendingBehaviorEventData.class),
    INVALIDTX("INVALIDTX", InvalidTxBehaviorEventData.class),
    CLAIM("CLAIM", ClaimFrequencyBasedEventData.class);

    private String text;
    public Class event;

    EventRequestType(String text , Class event) {
        this.text = text;
        this.event = event;
    }

    @Override
    public String toString() {
        return text;
    }
}
