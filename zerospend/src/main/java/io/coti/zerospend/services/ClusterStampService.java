package io.coti.zerospend.services;

import io.coti.basenode.data.*;
import io.coti.basenode.exceptions.ClusterStampException;
import io.coti.basenode.exceptions.ClusterStampValidationException;
import io.coti.basenode.services.BaseNodeClusterStampService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;


@Slf4j
@Service
public class ClusterStampService extends BaseNodeClusterStampService {

    @Value("${currency.genesis.address}")
    private String currencyAddress;
    @Value("${upload.clusterstamp}")
    private boolean uploadMajorClusterStamp;

    @Value("${aws.s3.bucket.name.clusterstamp}")
    private void setClusterStampBucketName(String clusterStampBucketName) {
        this.clusterStampBucketName = clusterStampBucketName;
    }

    @Override
    public void init() {
        super.init();
        if (uploadMajorClusterStamp) {
            uploadMajorClusterStamp();
        }
    }

    private void uploadMajorClusterStamp() {
        log.info("Starting to upload major clusterstamp");
        uploadClusterStamp(majorClusterStampName);
        log.info("Finished to upload major clusterstamp");
    }

    private void uploadClusterStamp(ClusterStampNameData clusterStampNameData) {
        awsService.uploadFileToS3(clusterStampBucketName, clusterStampFolder + getClusterStampFileName(clusterStampNameData));
    }

    protected void fillClusterStampNamesMap() {
        super.fillClusterStampNamesMap();
        if (majorClusterStampName == null) {
            handleMissingMajor();
        }

    }

    private void handleMissingMajor() {
        CurrencyData nativeCurrency = currencyService.getNativeCurrency();
        if (nativeCurrency == null) {
            throw new ClusterStampException("Unable to start zero spend server. Native token not found.");
        }
        handleNewCurrencyByType(nativeCurrency, ClusterStampType.MAJOR);
        uploadMajorClusterStamp = true;
    }

    private ClusterStampNameData handleNewCurrencyByType(CurrencyData currency, ClusterStampType clusterStampType) {
        ClusterStampNameData clusterStampNameData = new ClusterStampNameData(clusterStampType);
        generateOneLineClusterStampFile(clusterStampNameData, currency);
        addClusterStampName(clusterStampNameData);
        return clusterStampNameData;
    }

    private void generateOneLineClusterStampFile(ClusterStampNameData clusterStamp, CurrencyData currencyData) {
        ClusterStampData clusterStampData = createClusterStampDataFromCurrencyData(currencyData);
        clusterStampCrypto.signMessage(clusterStampData);
        createClusterStampFileFromClusterStampData(clusterStampData, clusterStampFolder, super.getClusterStampFileName(clusterStamp));
    }

    private ClusterStampData createClusterStampDataFromCurrencyData(CurrencyData currencyData) {
        ClusterStampData clusterStampData = new ClusterStampData();

        Hash addressHash = new Hash(this.currencyAddress);
        BigDecimal currencyAmountInAddress = currencyData.getTotalSupply();
        Hash currencyHash = currencyData.getHash();

        byte[] addressHashInBytes = addressHash.getBytes();
        byte[] addressCurrencyAmountInBytes = currencyAmountInAddress.stripTrailingZeros().toPlainString().getBytes();
        byte[] currencyHashInBytes = currencyHash.getBytes();
        byte[] balanceInBytes = ByteBuffer.allocate(addressHashInBytes.length + addressCurrencyAmountInBytes.length + currencyHashInBytes.length)
                .put(addressHashInBytes).put(addressCurrencyAmountInBytes).put(currencyHashInBytes).array();
        clusterStampData.getSignatureMessage().add(balanceInBytes);
        clusterStampData.getDataRows().add(new ClusterStampDataRow(addressHash, currencyAmountInAddress, currencyHash));
        clusterStampData.incrementMessageByteSize(balanceInBytes.length);

        return clusterStampData;
    }

    private String generateClusterStampLineFromNewCurrency(CurrencyData currencyData) {
        String clusterStampDelimiter = ",";
        StringBuilder sb = new StringBuilder();
        sb.append(this.currencyAddress).append(clusterStampDelimiter).append(currencyData.getTotalSupply().toString()).append(clusterStampDelimiter).append(currencyData.getHash());
        return sb.toString();
    }

    @Override
    protected void handleMissingRecoveryServer() {
        // Zero spend does nothing in this method.
    }

    @Override
    protected void handleClusterStampWithoutSignature(ClusterStampData clusterStampData, String clusterstampFileLocation) {
        clusterStampCrypto.signMessage(clusterStampData);
        updateClusterStampFileWithSignature(clusterStampData.getSignature(), clusterstampFileLocation);
        uploadMajorClusterStamp = true;
    }

    private void updateClusterStampFileWithSignature(SignatureData signature, String clusterStampFileLocation) {
        try (FileWriter clusterstampFileWriter = new FileWriter(clusterStampFileLocation, true);
             BufferedWriter clusterStampBufferedWriter = new BufferedWriter(clusterstampFileWriter)) {
            clusterStampBufferedWriter.newLine();
            clusterStampBufferedWriter.newLine();
            clusterStampBufferedWriter.append("# Signature");
            clusterStampBufferedWriter.newLine();
            clusterStampBufferedWriter.append("r," + signature.getR());
            clusterStampBufferedWriter.newLine();
            clusterStampBufferedWriter.append("s," + signature.getS());
        } catch (Exception e) {
            throw new ClusterStampValidationException("Exception at clusterstamp signing.", e);
        }
    }

    @Override
    protected void setClusterStampSignerHash(ClusterStampData clusterStampData) {
        clusterStampData.setSignerHash(networkService.getNetworkNodeData().getNodeHash());
    }

    public ClusterStampNameData handleNewToken(CurrencyData currencyData) {
        ClusterStampNameData clusterStampNameData = handleNewCurrencyByType(currencyData, ClusterStampType.TOKEN);
        uploadClusterStamp(clusterStampNameData);
        loadClusterStamp(clusterStampNameData);
        return clusterStampNameData;
    }

    protected void createClusterStampFileFromClusterStampData(ClusterStampData clusterStampData, String clusterStampDirPath, String clusterStampFileName) {
        String clusterStampFileLocation = clusterStampDirPath + "/" + clusterStampFileName;
        String clusterStampDelimiter = ",";
        try (FileWriter clusterStampFileWriter = new FileWriter(clusterStampFileLocation);
             BufferedWriter clusterStampBufferedWriter = new BufferedWriter(clusterStampFileWriter)) {
            for (ClusterStampDataRow clusterStampRow : clusterStampData.getDataRows()) {
                clusterStampBufferedWriter.append(clusterStampRow.getAddressHash().toString()).append(clusterStampDelimiter)
                        .append(clusterStampRow.getAmount().toString()).append(clusterStampDelimiter).append(clusterStampRow.getCurrencyHash().toString());
                clusterStampBufferedWriter.newLine();
            }
            clusterStampBufferedWriter.newLine();
            clusterStampBufferedWriter.append("# Signature");
            clusterStampBufferedWriter.newLine();
            clusterStampBufferedWriter.append("r," + clusterStampData.getSignature().getR());
            clusterStampBufferedWriter.newLine();
            clusterStampBufferedWriter.append("s," + clusterStampData.getSignature().getS());
        } catch (Exception e) {
            throw new ClusterStampValidationException("Exception at clusterstamp signing.", e);
        }
    }

}