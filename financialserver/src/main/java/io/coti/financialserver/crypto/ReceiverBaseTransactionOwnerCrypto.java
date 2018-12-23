package io.coti.financialserver.crypto;

import io.coti.basenode.crypto.CryptoHelper;
import io.coti.basenode.crypto.SignatureCrypto;
import io.coti.financialserver.data.ReceiverBaseTransactionOwnerData;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Service
public class ReceiverBaseTransactionOwnerCrypto extends SignatureCrypto<ReceiverBaseTransactionOwnerData> {

    @Override
    public byte[] getMessageInBytes(ReceiverBaseTransactionOwnerData rbtOwnerData) {

        int byteBufferLength;

        byte[] rbtHashInBytes = rbtOwnerData.getReceiverBaseTransactionHash().getBytes();
        byte[] merchantHashInBytes = rbtOwnerData.getMerchantHash().getBytes();

        byteBufferLength = rbtHashInBytes.length + merchantHashInBytes.length;
        ByteBuffer disputeDataBuffer = ByteBuffer.allocate(byteBufferLength);

        disputeDataBuffer.put(rbtHashInBytes);
        disputeDataBuffer.put(merchantHashInBytes);

        byte[] rbtOwnerDataInBytes = disputeDataBuffer.array();
        return CryptoHelper.cryptoHash(rbtOwnerDataInBytes).getBytes();
    }
}
