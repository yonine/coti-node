package io.coti.financialserver.http;

import io.coti.basenode.http.BaseResponse;
import io.coti.financialserver.http.data.DisputeEventResponseData;
import lombok.Data;

import java.util.List;

@Data
public class GetUnreadEventsResponse extends BaseResponse {

    private List<DisputeEventResponseData> unreadUserDisputeEvents;

    public GetUnreadEventsResponse(List<DisputeEventResponseData> unreadUserDisputeEvents) {
        super();

        this.unreadUserDisputeEvents = unreadUserDisputeEvents;
    }
}
