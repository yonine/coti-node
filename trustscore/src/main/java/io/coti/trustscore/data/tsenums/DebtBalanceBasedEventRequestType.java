package io.coti.trustscore.data.tsenums;

import io.coti.trustscore.data.tsevents.*;

public enum DebtBalanceBasedEventRequestType {
    DEBT("DEBT", DebtBalanceBasedEventData.class),
    CLOSEDEBT("CLOSEDEBT", CloseDebtBalanceBasedEventData.class);

    private String text;
    public Class event;

    DebtBalanceBasedEventRequestType(String text , Class event) {
        this.text = text;
        this.event = event;
    }

    @Override
    public String toString() {
        return text;
    }
}
