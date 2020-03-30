package io.coti.basenode.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.coti.basenode.data.interfaces.ISignValidatable;
import io.coti.basenode.data.interfaces.ISignable;
import lombok.Data;

import java.time.Instant;

@Data
public class CurrencyTypeRegistrationData extends CurrencyTypeData implements ISignable, ISignValidatable {

    private Hash currencyHash;

    public CurrencyTypeRegistrationData(Hash currencyHash, CurrencyTypeData currencyTypeData) {
        super(currencyTypeData);
        this.currencyHash = currencyHash;
    }

    public CurrencyTypeRegistrationData(Hash currencyHash, CurrencyType currencyType, Instant createTime) {
        this.currencyType = currencyType;
        this.createTime = createTime;
        this.currencyHash = currencyHash;
    }

    @JsonIgnore
    @Override
    public SignatureData getSignature() {
        return signature;
    }

    @Override
    public Hash getSignerHash() {
        return signerHash;
    }

    @Override
    public void setSignerHash(Hash signerHash) {
        this.signerHash = signerHash;
    }

    @JsonIgnore
    @Override
    public void setSignature(SignatureData signature) {
        this.signature = signature;
    }

}
