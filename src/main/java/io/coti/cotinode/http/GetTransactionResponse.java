package io.coti.cotinode.http;

import io.coti.cotinode.data.TransactionData;
import lombok.Data;

import java.util.List;

@Data
public class GetTransactionResponse extends Response {
    private TransactionData transactionData;

    public GetTransactionResponse(TransactionData transactionData) {
        super();
        this.transactionData = transactionData;
    }
}

