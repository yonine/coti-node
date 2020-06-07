package io.coti.dspnode.services;

import io.coti.basenode.communication.interfaces.IPropagationPublisher;
import io.coti.basenode.communication.interfaces.IReceiver;
import io.coti.basenode.data.NodeType;
import io.coti.basenode.data.messages.StateMessage;
import io.coti.basenode.data.messages.StateMessageClusterStampExecutePayload;
import io.coti.basenode.data.messages.StateMessageClusterStampInitiatedPayload;
import io.coti.basenode.data.messages.StateMessageLastClusterStampIndexPayload;
import io.coti.basenode.services.BaseNodeClusterStampService;
import io.coti.basenode.services.ClusterService;
import io.coti.basenode.services.EventSchedulerService;
import io.coti.basenode.services.TransactionIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;

import static java.lang.Math.min;

@Slf4j
@Service
public class ClusterStampService extends BaseNodeClusterStampService {

    private static final long MAX_CLUSTERSTAMP_DELAY = 100;
    @Autowired
    private IPropagationPublisher propagationPublisher;
    @Autowired
    private IReceiver receiver;
    @Autowired
    private ClusterService clusterService;
    @Autowired
    private TransactionIndexService transactionIndexService;
    @Autowired
    private EventSchedulerService eventSchedulerService;

    @Override
    public void clusterStampInitiate(StateMessage stateMessage, StateMessageClusterStampInitiatedPayload stateMessageClusterstampInitiatedPayload) {

        Instant eventTime = Instant.now().plusSeconds(min(MAX_CLUSTERSTAMP_DELAY, stateMessageClusterstampInitiatedPayload.getDelay()));
        receiver.setMessageQueuePause(eventSchedulerService.scheduleEvent("CLUSTER_STUMP_INITIATE_DELAY", eventTime, 1000 , false,
                null, receiver::endMessageQueuePause));
        // todo toExecuteIfFinished should be set to emergency procedure
        propagationPublisher.propagate(stateMessage, Collections.singletonList(NodeType.FullNode));
    }

    @Override
    public boolean checkLastConfirmedIndex(StateMessageLastClusterStampIndexPayload stateMessageLastClusterStampIndexPayload) {
        long lastConfirmedIndex = clusterService.maxIndexOfNotConfirmed();
        if (lastConfirmedIndex <= 0) {
            lastConfirmedIndex = transactionIndexService.getLastTransactionIndexData().getIndex();
        }
        return lastConfirmedIndex == stateMessageLastClusterStampIndexPayload.getLastIndex();
    }

    @Override
    public void clusterStampExecute(StateMessage stateMessage, StateMessageClusterStampExecutePayload stateMessageClusterStampExecutePayload) {
        propagationPublisher.propagate(stateMessage, Collections.singletonList(NodeType.FullNode));
    }
}
