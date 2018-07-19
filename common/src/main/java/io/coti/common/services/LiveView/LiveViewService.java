package io.coti.common.services.LiveView;

import io.coti.common.data.GraphData;
import io.coti.common.data.NodeData;
import io.coti.common.data.TransactionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class LiveViewService {

    @Autowired
    private WebSocketSender webSocketSender;

    private GraphData graphData;

    public LiveViewService() {
        this.graphData = new GraphData();
        this.graphData.nodes = new LinkedList<>();
    }

    public GraphData getFullGraph() {
        return graphData;
    }

    public void addNode(TransactionData transactionData) {
        NodeData nodeData = new NodeData();
        nodeData.setId(transactionData.getHash().toHexString());
        nodeData.setTrustScore(transactionData.getSenderTrustScore());
        nodeData.setGenesis(transactionData.isZeroSpend());
        
        if (transactionData.isSource()) {
            nodeData.setStatus(0);
        } else {
            nodeData.setStatus(transactionData.isTrustChainConsensus() ? 2 : 1);
        }

        if (transactionData.getLeftParentHash() != null) {
            nodeData.setLeftParent(transactionData.getLeftParentHash().toHexString());
        }
        if (transactionData.getRightParentHash() != null) {
            nodeData.setRightParent(transactionData.getRightParentHash().toHexString());
        }
        setNodeDataDatesFromTransactionData(transactionData, nodeData);
        graphData.nodes.add(nodeData);
        webSocketSender.sendNode(nodeData);
    }

    public void updateNodeStatus(TransactionData transactionData, int newStatus) {
        NodeData nodeData = new NodeData();
        nodeData.setId(transactionData.getHash().toHexString());
        nodeData.setTrustScore(transactionData.getSenderTrustScore());
        int currentIndex = graphData.nodes.indexOf(nodeData);
        NodeData newNode = graphData.nodes.get(currentIndex);
        newNode.setStatus(newStatus);
        setNodeDataDatesFromTransactionData(transactionData, newNode);
        webSocketSender.sendNode(newNode);
    }

    public void setNodeDataDatesFromTransactionData(TransactionData transactionData, NodeData nodeData) {
        if (transactionData.getAttachmentTime()!= null && transactionData.getTransactionConsensusUpdateTime()!= null) {
            nodeData.setTccDuration( (transactionData.getTransactionConsensusUpdateTime().getTime() - transactionData.getAttachmentTime().getTime())/ 1000);
        }
    }
}