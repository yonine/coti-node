package io.coti.nodemanager.http.data;

import io.coti.nodemanager.data.NetworkNodeStatus;
import io.coti.nodemanager.data.NodeNetworkDataRecord;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NodeNetworkResponseData implements Serializable {

    private static final long serialVersionUID = 7814283160669885668L;

    private LocalDateTime recordDateTime;
    private NetworkNodeStatus nodeStatus;

    public NodeNetworkResponseData() {
    }

    public NodeNetworkResponseData(NodeNetworkDataRecord nodeNetworkDataRecord) {
        this.recordDateTime = nodeNetworkDataRecord.getRecordStartDateTime();
        this.nodeStatus = nodeNetworkDataRecord.getNodeStatus();
    }
}
