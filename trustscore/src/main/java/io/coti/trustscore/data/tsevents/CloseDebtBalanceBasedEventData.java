package io.coti.trustscore.data.tsevents;

import io.coti.basenode.data.Hash;
import io.coti.trustscore.data.tsenums.FinalEventType;
import io.coti.trustscore.http.InsertDebtBalanceBasedScoreRequest;
import lombok.Data;

@Data
public class CloseDebtBalanceBasedEventData extends BalanceBasedEventData {

    private static final long serialVersionUID = -7874193033558594029L;
    private Hash plaintiffUserHash;

    public CloseDebtBalanceBasedEventData() {
    }

    public CloseDebtBalanceBasedEventData(InsertDebtBalanceBasedScoreRequest request) {
        super(request);
        this.plaintiffUserHash = request.getOtherUserHash();
        this.amount = request.getAmount();
    }
}