package io.coti.basenode.data;

import io.coti.basenode.data.interfaces.ISignValidatable;
import io.coti.basenode.data.interfaces.ISignable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
public class ClusterStampDataRow {
    private Hash addressHash;
    private BigDecimal amount;
    private Hash currencyHash;

    public ClusterStampDataRow(Hash addressHash, BigDecimal amount, Hash currencyHash) {
        this.addressHash = addressHash;
        this.amount = amount;
        this.currencyHash = currencyHash;
    }
}