package io.coti.trustscore.data.tsenums;

import io.coti.trustscore.data.tsevents.CloseDepositBalanceBasedEventData;
import io.coti.trustscore.data.tsevents.DepositBalanceBasedEventData;

public enum DepositBalanceBasedEventRequestType {
    DEPOSIT("DEPOSIT", DepositBalanceBasedEventData.class),
    CLOSEDEPOSIT("CLOSEDEPOSIT", CloseDepositBalanceBasedEventData.class);

    private String text;
    public Class event;

    DepositBalanceBasedEventRequestType(String text , Class event) {
        this.text = text;
        this.event = event;
    }

    @Override
    public String toString() {
        return text;
    }
}
