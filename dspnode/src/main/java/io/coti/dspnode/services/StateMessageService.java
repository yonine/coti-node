package io.coti.dspnode.services;

import io.coti.basenode.data.Hash;
import io.coti.basenode.data.messages.*;
import io.coti.basenode.services.BaseNodeStateMessageService;
import io.coti.basenode.services.interfaces.IClusterStampService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class StateMessageService extends BaseNodeStateMessageService {

    @Autowired
    private IClusterStampService clusterStampService;

    @Override
    public void continueHandleStateMessage(StateMessage stateMessage) {
        switch (stateMessage.getMessagePayload().getGeneralMessageType()) {
            case CLUSTER_STAMP_INITIATED:
                clusterStampService.clusterStampInitiate(stateMessage, (StateMessageClusterStampInitiatedPayload) stateMessage.getMessagePayload());
                break;
            case CLUSTER_STAMP_PREPARE_INDEX:
                clusterStampService.clusterStampContinueWithIndex(stateMessage);
                boolean vote = clusterStampService.checkLastConfirmedIndex((StateMessageLastClusterStampIndexPayload) stateMessage.getMessagePayload());
                generalVoteService.startCollectingVotes(stateMessage, generalVoteService.castVoteForClusterStampIndex(stateMessage.getHash(), vote));
                if (vote) {
                    clusterStampService.calculateClusterStampDataAndHashes();  // todo separate it to a thread
                }
                break;
            case CLUSTER_STAMP_PREPARE_HASH:
                clusterStampService.clusterStampContinueWithHash(stateMessage);
                Hash candidateClusterStampHash = clusterStampService.getCandidateClusterStampHash();
                generalVoteService.startCollectingVotes(stateMessage, generalVoteService.castVoteForClusterStampHash(stateMessage.getHash(),
                        clusterStampService.checkClusterStampHash((StateMessageClusterStampHashPayload) stateMessage.getMessagePayload()), candidateClusterStampHash));
                Thread waitForHistoryNodesAndCastVote = new Thread(() -> {
                    try {
                        Instant waitForHistoryNodesTill = Instant.now().plusSeconds(clusterStampService.CLUSTER_STAMP_TIMEOUT);
                        while (Instant.now().isBefore(waitForHistoryNodesTill)) {
                            if (clusterStampService.isAgreedHistoryNodesNumberEnough()) {
                                generalVoteService.castVoteForClusterStampHash(stateMessage.getHash(), clusterStampService.checkClusterStampHash((StateMessageClusterStampHashPayload) stateMessage.getMessagePayload()), candidateClusterStampHash);
                                break;
                            }
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException e) {
                        log.info(String.format("WaitForHistoryNodesAndCastVote interrupted %s", e));
                        Thread.currentThread().interrupt();
                    }
                });
                waitForHistoryNodesAndCastVote.start();

                break;
            case CLUSTER_STAMP_CONTINUE:
                clusterStampService.clusterStampContinue(stateMessage);
                break;
            case CLUSTER_STAMP_EXECUTE:
                clusterStampService.clusterStampExecute(stateMessage, (StateMessageClusterStampExecutePayload) stateMessage.getMessagePayload());
                break;
            default:
                log.error("Unexpected message type: {}", stateMessage.getMessagePayload().getGeneralMessageType());
        }
    }

}