package io.coti.trustscore.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coti.basenode.crypto.ExpandedTransactionTrustScoreCrypto;
import io.coti.basenode.data.*;
import io.coti.basenode.http.GetUserTrustScoreResponse;
import io.coti.basenode.http.Response;
import io.coti.basenode.http.data.TransactionTrustScoreResponseData;
import io.coti.basenode.http.interfaces.IResponse;
import io.coti.basenode.services.interfaces.IBalanceService;
import io.coti.trustscore.config.rules.RulesData;
import io.coti.trustscore.config.rules.ScoreRules;
import io.coti.trustscore.crypto.*;
import io.coti.trustscore.data.AddressUserIndexData;
import io.coti.trustscore.data.UnlinkedAddressData;
import io.coti.trustscore.data.UserTrustScoreData;
import io.coti.trustscore.data.tsbuckets.*;
import io.coti.trustscore.data.tsenums.DocumentRequestType;
import io.coti.trustscore.data.tsenums.EventRequestType;
import io.coti.trustscore.data.tsenums.EventType;
import io.coti.trustscore.data.tsenums.UserType;
import io.coti.trustscore.data.tsevents.*;
import io.coti.trustscore.http.*;
import io.coti.trustscore.model.AddressUserIndex;
import io.coti.trustscore.model.Buckets;
import io.coti.trustscore.model.UnlinkedAddresses;
import io.coti.trustscore.model.UserTrustScores;
import io.coti.trustscore.services.interfaces.IBucketService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static io.coti.basenode.http.BaseNodeHttpStringConstants.STATUS_ERROR;
import static io.coti.trustscore.http.HttpStringConstants.*;

@Slf4j
@Service
@Data
public class TrustScoreService {

    @Value("${kycserver.public.key:}")
    private String[] kycServerPublicKeys;

    @Autowired
    private ExpandedTransactionTrustScoreCrypto expandedTransactionTrustScoreCrypto;
    @Autowired
    private SetKycTrustScoreCrypto setKycTrustScoreCrypto;
    @Autowired
    private TrustScoreUserTypeCrypto trustScoreUserTypeCrypto;
    @Autowired
    private InsertDocumentScoreCrypto insertDocumentScoreCrypto;
    @Autowired
    private InsertEventScoreCrypto insertEventScoreCrypto;
    @Autowired
    private InsertChargeBackFrequencyBasedScoreCrypto insertChargeBackFrequencyBasedScoreCrypto;
    @Autowired
    private SetUserZeroTrustFlagCrypto setUserZeroTrustFlagCrypto;
    @Autowired
    private InsertDebtBalanceBasedScoreCrypto insertDebtBalanceBasedScoreCrypto;
    @Autowired
    private InsertDepositBalanceBasedScoreCrypto insertDepositBalanceBasedScoreCrypto;

    @Autowired
    private UserTrustScores userTrustScores;
    @Autowired
    private Buckets buckets;
    @Autowired
    private AddressUserIndex addressUserIndex;
    @Autowired
    private UnlinkedAddresses unlinkedAddresses;

    @Autowired
    private BucketDebtBalanceBasedScoreService bucketDebtBalanceBasedScoreService;
    @Autowired
    private BucketDepositBalanceBasedScoreService bucketDepositBalanceBasedScoreService;
    @Autowired
    private BucketDocumentScoreService bucketDocumentScoreService;
    @Autowired
    private BucketEventScoreService bucketEventScoreService;
    @Autowired
    private BucketFrequencyBasedScoreService bucketFrequencyBasedScoreService;
    @Autowired
    private BucketTransactionScoreService bucketTransactionScoreService;
    @Autowired
    private BucketChargeBackFrequencyBasedScoreService bucketChargeBackFrequencyBasedScoreService;

    @Autowired
    private IBalanceService balanceService;
    @Autowired
    private RollingReserveService rollingReserveService;

    @Autowired
    private GetTransactionTrustScoreRequestCrypto getTransactionTrustScoreRequestCrypto;

    private List<IBucketService> bucketScoreServiceList;
    private RulesData rulesData;
    private Map<Hash, Hash> lockUnlinkedAddressesHashMap = new ConcurrentHashMap<>();
    private Map<Hash, Hash> lockUserTrustScoresHashMap = new ConcurrentHashMap<>();
    private Map<Hash, Hash> lockBucketsHashMap = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        log.info("{} is up", this.getClass().getSimpleName());
        bucketScoreServiceList = new ArrayList<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream jsonConfigStream = new ClassPathResource("trustScoreRules.json").getInputStream();
            rulesData = objectMapper.readValue(jsonConfigStream, RulesData.class);
            log.debug(rulesData.toString());
        } catch (IOException e) {
            log.error("Error reading from JSON file", e);
            log.error("Shutting down!");
            System.exit(1);
        }

        Map<String, ScoreRules> classToScoreRulesMap = rulesData.getClassToScoreRulesMap();
        BucketDocumentScoreService.init(classToScoreRulesMap);
        BucketEventScoreService.init(classToScoreRulesMap);
        BucketFrequencyBasedScoreService.init(classToScoreRulesMap);
        BucketTransactionScoreService.init(classToScoreRulesMap);
        BucketChargeBackFrequencyBasedScoreService.init(classToScoreRulesMap);
        BucketDebtBalanceBasedScoreService.init(classToScoreRulesMap);
        BucketDepositBalanceBasedScoreService.init(classToScoreRulesMap);

        bucketScoreServiceList.add(bucketDocumentScoreService);
        bucketScoreServiceList.add(bucketEventScoreService);
        bucketScoreServiceList.add(bucketFrequencyBasedScoreService);
        bucketScoreServiceList.add(bucketTransactionScoreService);
        bucketScoreServiceList.add(bucketChargeBackFrequencyBasedScoreService);
        bucketScoreServiceList.add(bucketDebtBalanceBasedScoreService);
        bucketScoreServiceList.add(bucketDepositBalanceBasedScoreService);
    }

    public ResponseEntity<IResponse> setKycTrustScore(SetKycTrustScoreRequest request) {
        SetKycTrustScoreResponse kycTrustScoreResponse;

        try {
            log.info("Setting KYC trust score: userHash =  {}, KTS = {}, userType =  {}", request.userHash, request.kycTrustScore, request.userType);
            if (request.kycTrustScore <= 0 || request.kycTrustScore > 100) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new Response(KYC_TRUST_INCORRECT_VALUE, STATUS_ERROR));
            }
            if (!checkIfSignerInList(request.getSignerHash())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
            }
            if (!setKycTrustScoreCrypto.verifySignature(request)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(KYC_TRUST_SCORE_AUTHENTICATION_ERROR, STATUS_ERROR));
            }

            UserTrustScoreData oldUserTrustScoreData = userTrustScores.getByHash(request.userHash);
            if (oldUserTrustScoreData == null) {
                UserTrustScoreData newUserTrustScoreData = new UserTrustScoreData(request.userHash,
                        UserType.enumFromString(request.userType));
                createBuckets(newUserTrustScoreData);
                userTrustScores.put(newUserTrustScoreData);
            } else {
                if (!oldUserTrustScoreData.getUserType().equals(UserType.enumFromString(request.userType))) {
                    return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(new Response(KYC_TRUST_DIFFERENT_TYPE, STATUS_ERROR));
                }
            }

            KYCDocumentEventData kYCDocumentScoreData = new KYCDocumentEventData(request);
            BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(request.userHash, EventType.DOCUMENT_SCORE));
            bucketData.addScoreToBucketMap(kYCDocumentScoreData);
            bucketDocumentScoreService.addScoreToCalculations(kYCDocumentScoreData, (BucketDocumentEventData) bucketData);
            buckets.put(bucketData);

            kycTrustScoreResponse = new SetKycTrustScoreResponse(request.userHash.toHexString(), request.getKycTrustScore());

            insertFillQuestionnaireEvent(request.userHash, DocumentRequestType.KYC);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(kycTrustScoreResponse);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(KYC_TRUST_SCORE_ERROR, STATUS_ERROR));
        }
    }

    public ResponseEntity<IResponse> insertDocumentScore(InsertDocumentScoreRequest request) {
        try {
            if (!checkIfSignerInList(request.getSignerHash())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
            }
            if (!insertDocumentScoreCrypto.verifySignature(request)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(INSERT_DOCUMENT_AUTHENTICATION_ERROR, STATUS_ERROR));
            }

            DocumentEventData documentScoreData = (DocumentEventData) request.documentType.event.getDeclaredConstructor(InsertDocumentScoreRequest.class).newInstance(request);

            BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(request.userHash, EventType.DOCUMENT_SCORE));
            bucketData.addScoreToBucketMap(documentScoreData);
            bucketDocumentScoreService.addScoreToCalculations(documentScoreData, (BucketDocumentEventData) bucketData);
            buckets.put(bucketData);

            insertFillQuestionnaireEvent(request.userHash, request.getDocumentType());

            InsertDocumentScoreResponse insertDocumentScoreResponse = new InsertDocumentScoreResponse(request.userHash.toHexString(),
                    request.getDocumentType().toString(), request.getScore());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(insertDocumentScoreResponse);

        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(INSERT_DOCUMENT_SCORE_ERROR, STATUS_ERROR));
        }
    }

    public ResponseEntity<IResponse> insertEventScore(InsertEventScoreRequest request) {
        try {
            if (!checkIfSignerInList(request.getSignerHash())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
            }
            if (!insertEventScoreCrypto.verifySignature(request)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(INSERT_EVENT_AUTHENTICATION_ERROR, STATUS_ERROR));
            }

            if (request.eventType != EventRequestType.CLAIM) {
                BehaviorEventData eventScoreData = (BehaviorEventData) request.eventType.event.getDeclaredConstructor(InsertEventScoreRequest.class).newInstance(request);
                BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(request.userHash, EventType.EVENT_SCORE));

                if (bucketData.getEventDataHashToEventDataMap().get(request.eventIdentifier) != null) {
                    return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(new Response(EVENT_EXIST, STATUS_ERROR));
                }

                bucketData.addScoreToBucketMap(eventScoreData);
                bucketEventScoreService.addScoreToCalculations(eventScoreData, (BucketBehaviorEventData) bucketData);

                buckets.put(bucketData);
            } else {
                FrequencyBasedEventData frequencyBasedScoreData = (FrequencyBasedEventData) request.eventType.event.getDeclaredConstructor(InsertEventScoreRequest.class).newInstance(request);
                BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(request.userHash, EventType.FREQUENCY_BASED_SCORE));

                if (bucketData.getEventDataHashToEventDataMap().get(request.eventIdentifier) != null) {
                    return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(new Response(EVENT_EXIST, STATUS_ERROR));
                }

                bucketData.addScoreToBucketMap(frequencyBasedScoreData);
                bucketFrequencyBasedScoreService.addScoreToCalculations(frequencyBasedScoreData, (BucketFrequencyBasedEventData) bucketData);

                buckets.put(bucketData);

            }

            InsertEventScoreResponse insertEventScoreResponse = new InsertEventScoreResponse(request.userHash.toHexString(),
                    request.getEventType().toString(), request.getEventIdentifier().toHexString());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(insertEventScoreResponse);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(INSERT_EVENT_SCORE_ERROR, STATUS_ERROR));
        }
    }


    public ResponseEntity<IResponse> insertChargeBackFrequencyBasedScore(InsertChargeBackFrequencyBasedScoreRequest request) {
        try {
            if (!checkIfSignerInList(request.getSignerHash())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
            }
            if (!insertChargeBackFrequencyBasedScoreCrypto.verifySignature(request)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(INSERT_EVENT_AUTHENTICATION_ERROR, STATUS_ERROR));
            }

            ChargeBackFrequencyBasedEventData chargeBackFrequencyBasedScoreData = new ChargeBackFrequencyBasedEventData(request);

            BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(request.userHash, EventType.CHARGEBACK_SCORE));

            if (bucketData.getUserType() != UserType.MERCHANT) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new Response(String.format(USER_NOT_MERCHANT, request.userHash), STATUS_ERROR));
            }

            if (bucketData.getEventDataHashToEventDataMap().get(request.eventIdentifier) != null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new Response(EVENT_EXIST, STATUS_ERROR));
            }

            bucketData.addScoreToBucketMap(chargeBackFrequencyBasedScoreData);
            bucketChargeBackFrequencyBasedScoreService.addScoreToCalculations(chargeBackFrequencyBasedScoreData, (BucketChargeBackFrequencyBasedEventData) bucketData);

            buckets.put(bucketData);

            InsertChargeBackFrequencyBasedScoreResponse insertChargeBackFrequencyBasedScoreResponse =
                    new InsertChargeBackFrequencyBasedScoreResponse(request.userHash.toHexString(),
                            request.getEventIdentifier().toHexString(), request.getTransactionHash().toHexString(), request.getAmount().toString());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(insertChargeBackFrequencyBasedScoreResponse);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(INSERT_EVENT_SCORE_ERROR, STATUS_ERROR));
        }
    }

    private void insertFillQuestionnaireEvent(Hash userHash, DocumentRequestType documentRequestType) {
        Hash eventIdentifier = new Hash(DatatypeConverter.printHexBinary(Instant.now().toString().getBytes()).toLowerCase());
        FillQuestionnaireBehaviorEventData fillQuestionnaireEventScoreData = new FillQuestionnaireBehaviorEventData(eventIdentifier);
        BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(userHash, EventType.EVENT_SCORE));
        if (bucketData.getEventDataHashToEventDataMap().get(eventIdentifier) == null) {
            bucketData.addScoreToBucketMap(fillQuestionnaireEventScoreData);
            bucketEventScoreService.addScoreToCalculations(fillQuestionnaireEventScoreData, (BucketBehaviorEventData) bucketData);
            buckets.put(bucketData);
        }
    }

    public ResponseEntity<IResponse> insertDebtBalanceBasedScore(InsertDebtBalanceBasedScoreRequest request) {
        try {
            if (!checkIfSignerInList(request.getSignerHash())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
            }
            if (!insertDebtBalanceBasedScoreCrypto.verifySignature(request)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(INSERT_EVENT_AUTHENTICATION_ERROR, STATUS_ERROR));
            }

            BalanceBasedEventData balanceBasedScoreData = (BalanceBasedEventData) request.eventType.event.getDeclaredConstructor(InsertDebtBalanceBasedScoreRequest.class).newInstance(request);
            BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(request.userHash, EventType.DEBT_BALANCE_BASED_SCORE));

            if (bucketData.getEventDataHashToEventDataMap().get(request.eventIdentifier) != null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new Response(EVENT_EXIST, STATUS_ERROR));
            }

            bucketData.addScoreToBucketMap(balanceBasedScoreData);
            bucketDebtBalanceBasedScoreService.addScoreToCalculations(balanceBasedScoreData, (BucketDebtBalanceBasedEventData) bucketData);

            buckets.put(bucketData);

            InsertDebtBalanceBasedScoreResponse insertDebtBalanceBasedScoreResponse = new InsertDebtBalanceBasedScoreResponse(request.userHash.toHexString(),
                    request.getEventType().toString(), request.getAmount().toString(), request.getEventIdentifier().toHexString(), request.getOtherUserHash().toHexString());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(insertDebtBalanceBasedScoreResponse);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(INSERT_EVENT_SCORE_ERROR, STATUS_ERROR));
        }
    }

    public ResponseEntity<IResponse> insertDepositBalanceBasedScore(InsertDepositBalanceBasedScoreRequest request) {
        try {
            if (!checkIfSignerInList(request.getSignerHash())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
            }
            if (!insertDepositBalanceBasedScoreCrypto.verifySignature(request)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(INSERT_EVENT_AUTHENTICATION_ERROR, STATUS_ERROR));
            }

            BalanceBasedEventData balanceBasedScoreData = (BalanceBasedEventData) request.eventType.event.getDeclaredConstructor(InsertDepositBalanceBasedScoreRequest.class).newInstance(request);
            BucketData bucketData = (BucketData) buckets.getByHash(getBucketHashByUserHashAndScoreType(request.userHash, EventType.DEPOSIT_BALANCE_BASED_SCORE));

            if (bucketData.getEventDataHashToEventDataMap().get(request.eventIdentifier) != null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new Response(EVENT_EXIST, STATUS_ERROR));
            }

            bucketData.addScoreToBucketMap(balanceBasedScoreData);
            bucketDepositBalanceBasedScoreService.addScoreToCalculations(balanceBasedScoreData, (BucketDepositBalanceBasedEventData) bucketData);

            buckets.put(bucketData);

            InsertDepositBalanceBasedScoreResponse insertDepositBalanceBasedScoreResponse = new InsertDepositBalanceBasedScoreResponse(request.userHash.toHexString(),
                    request.getEventType().toString(), request.getAmount().toString(), request.getEventIdentifier().toHexString());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(insertDepositBalanceBasedScoreResponse);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(INSERT_EVENT_SCORE_ERROR, STATUS_ERROR));
        }
    }

    public ResponseEntity<IResponse> setUserType(SetUserTypeRequest request) {
        try {
            log.info("Setting UserType: " + request.getUserHash() + "=" + request.getUserType());

            UserTrustScoreData userTrustScoreData = userTrustScores.getByHash(request.userHash);
            if (userTrustScoreData == null) {
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new Response(USER_HASH_IS_NOT_IN_DB, STATUS_ERROR));
            }

            if (!checkIfSignerInList(request.getSignerHash())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
            }
            if (!trustScoreUserTypeCrypto.verifySignature(request)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(SET_USER_TYPE_AUTHENTICATION_ERROR, STATUS_ERROR));
            }
            if (!changingIsLegal(userTrustScoreData)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new Response(USER_TYPE_ALREADY_UPDATED, STATUS_ERROR));
            }

            UserType userType = UserType.enumFromString(request.getUserType());
            userTrustScoreData.setUserType(userType);
            userTrustScores.put(userTrustScoreData);
            updateUserTypeInBuckets(userTrustScoreData);

            SetUserTypeResponse setUserTypeResponse = new SetUserTypeResponse(userType, request.getUserHash());
            return ResponseEntity.status(HttpStatus.OK)
                    .body(setUserTypeResponse);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(USER_TYPE_SET_ERROR, STATUS_ERROR));
        }
    }


    public ResponseEntity<IResponse> getUserTrustScore(Hash userHash) {
        UserTrustScoreData userTrustScoreData = userTrustScores.getByHash(userHash);
        if (userTrustScoreData == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response(NON_EXISTING_USER_MESSAGE, STATUS_ERROR));
        }
        double currentTrustScore = calculateUserTrustScore(userTrustScoreData);
        GetUserTrustScoreResponse getUserTrustScoreResponse = new GetUserTrustScoreResponse(userHash.toString(), currentTrustScore, userTrustScoreData.getUserType().toString());
        return ResponseEntity.status(HttpStatus.OK).body(getUserTrustScoreResponse);
    }


    public ResponseEntity<IResponse> getTransactionTrustScore(GetTransactionTrustScoreRequest getTransactionTrustScoreRequest) {

        if (!getTransactionTrustScoreRequestCrypto.verifySignature(getTransactionTrustScoreRequest)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST).body(new Response(BAD_SIGNATURE_ON_TRUST_SCORE_FOR_TRANSACTION));
        }

        Hash userHash = getTransactionTrustScoreRequest.getUserHash();
        Hash transactionHash = getTransactionTrustScoreRequest.getTransactionHash();
        UserTrustScoreData userTrustScoreData = userTrustScores.getByHash(userHash);
        if (userTrustScoreData == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response(NON_EXISTING_USER_MESSAGE, STATUS_ERROR));
        }

        double currentTransactionsTrustScore = calculateUserTrustScore(userTrustScoreData);
        ExpandedTransactionTrustScoreData expandedTransactionTrustScoreData = new ExpandedTransactionTrustScoreData(userHash, transactionHash, currentTransactionsTrustScore);
        expandedTransactionTrustScoreCrypto.signMessage(expandedTransactionTrustScoreData);
        TransactionTrustScoreData transactionTrustScoreData = new TransactionTrustScoreData(expandedTransactionTrustScoreData);
        TransactionTrustScoreResponseData transactionTrustScoreResponseData = new TransactionTrustScoreResponseData(transactionTrustScoreData);
        GetTransactionTrustScoreResponse getTransactionTrustScoreResponse = new GetTransactionTrustScoreResponse(transactionTrustScoreResponseData);
        return ResponseEntity.status(HttpStatus.OK).body(getTransactionTrustScoreResponse);
    }

    public ResponseEntity<IResponse> getUserTrustScoreComponents(Hash userHash) {
        UserTrustScoreData userTrustScoreData = userTrustScores.getByHash(userHash);
        if (userTrustScoreData == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response(NON_EXISTING_USER_MESSAGE, STATUS_ERROR));
        }

        List<BucketData> bucketDataList = new ArrayList<>();
        for (IBucketService bucketService : bucketScoreServiceList) {
            BucketData bucketData =
                    (BucketData) buckets.getByHash(userTrustScoreData.getEventTypeToBucketHashMap().get(bucketService.getScoreType()));
            bucketDataList.add(bucketData);
        }

        GetUserTrustScoreComponentsResponse getUserTrustScoreComponentsResponse = new GetUserTrustScoreComponentsResponse(userTrustScoreData, bucketDataList);
        return ResponseEntity.status(HttpStatus.OK).body(getUserTrustScoreComponentsResponse);
    }

    public double calculateUserTrustScore(UserTrustScoreData userTrustScoreData) {

        if (userTrustScoreData.getZeroTrustFlag() != null && userTrustScoreData.getZeroTrustFlag()) {
            return 0;
        }

        double trustScore = 10;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (IBucketService bucketService : bucketScoreServiceList) {
            BucketData bucketData =
                    (BucketData) buckets.getByHash(userTrustScoreData.getEventTypeToBucketHashMap().get(bucketService.getScoreType()));
            if (bucketData != null) {
                LocalDate previousUpdate = bucketData.getLastUpdate();

                trustScore += bucketService.getBucketSumScore(bucketData);

                if (!previousUpdate.equals(today)) {
                    buckets.put(bucketData);
                }
            }
        }
        return Math.min(Math.max(trustScore, 0.1), 100.0);
    }


    public ResponseEntity<IResponse> purgeUser(PurgeUserRequest request) {

        try {
            log.info("Purging: userHash =  {}", request.getUserHash());

            UserTrustScoreData userTrustScoreData = userTrustScores.getByHash(request.userHash);

            if (userTrustScoreData != null) {

                for (EventType eventType : userTrustScoreData.getEventTypeToBucketHashMap().keySet()) {
                    BucketData bucketData =
                            (BucketData) buckets.getByHash(userTrustScoreData.getEventTypeToBucketHashMap().get(eventType));
                    buckets.delete(bucketData);
                }

                userTrustScores.delete(userTrustScoreData);
            }

            rollingReserveService.purgeMerchantRollingReserveAddress(request.getUserHash());

            Consumer consumerRD = addressRecord -> {
                AddressUserIndexData addressUserIndexData = (AddressUserIndexData) addressRecord;
                if (addressUserIndexData.getUserHash().equals(request.getUserHash())) {
                    UnlinkedAddressData unlinkedAddressData = new UnlinkedAddressData(addressUserIndexData.getAddress());
                    unlinkedAddresses.put(unlinkedAddressData);
                    addressUserIndex.delete(addressUserIndexData);    // todo test it
                }
            };
            addressUserIndex.forEach(consumerRD);

            // does not restore balances maps for unlinked addresses

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new PurgeUserResponse(request.getUserHash()));

        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(PURGING_USER_ERROR, STATUS_ERROR));
        }
    }

    public ResponseEntity<IResponse> setUserZeroTrustFlag(SetUserZeroTrustFlagSignedRequest request) {

        if (!checkIfSignerInList(request.getSignerHash())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new Response(UNKNOWN_SIGNER_ERROR, STATUS_ERROR));
        }
        if (!setUserZeroTrustFlagCrypto.verifySignature(request)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new Response(ZERO_TRUST_FLAG_AUTHENTICATION_ERROR, STATUS_ERROR));
        }

        SetUserZeroTrustFlagRequest setUserZeroTrustFlagRequest = new SetUserZeroTrustFlagRequest();
        setUserZeroTrustFlagRequest.setUserHash(request.getUserHash());
        setUserZeroTrustFlagRequest.setZeroTrustFlag(request.isZeroTrustFlag());
        return doSetUserZeroTrustFlag(setUserZeroTrustFlagRequest);
    }

    public ResponseEntity<IResponse> setUserZeroTrustFlag(SetUserZeroTrustFlagRequest request) {
        return doSetUserZeroTrustFlag(request);
    }

    public ResponseEntity<IResponse> doSetUserZeroTrustFlag(SetUserZeroTrustFlagRequest request) {
        try {
            log.info("Setting Zero Trust Flag: userHash =  {}, ZTF = {}", request.getUserHash(), request.isZeroTrustFlag());

            UserTrustScoreData userTrustScoreData = userTrustScores.getByHash(request.getUserHash());
            if (userTrustScoreData == null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new Response(NON_EXISTING_USER_MESSAGE, STATUS_ERROR));
            } else {
                userTrustScoreData.setZeroTrustFlag(request.isZeroTrustFlag());
                userTrustScores.put(userTrustScoreData);

                return ResponseEntity.status(HttpStatus.OK)
                        .body(new SetUserZeroTrustFlagResponse(userTrustScoreData));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response(ZERO_TRUST_FLAG_ERROR, STATUS_ERROR));
        }
    }

    public synchronized void addTransactionToTsCalculation(TransactionData transactionData) {
        try {
            if (EnumSet.of(TransactionType.ZeroSpend, TransactionType.Initial).contains(transactionData.getType()) || transactionData.getDspConsensusResult() == null ||
                    !transactionData.getDspConsensusResult().isDspConsensus()) {
                return;
            }

            LocalDate transactionConsensusDate = transactionData.getDspConsensusResult().getIndexingTime().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);

            if (currentDate.equals(transactionConsensusDate)) {
                if (transactionData.getSenderHash() != null) {
                    addTransactionToUserTs(transactionData, transactionData.getSenderHash());
                }
                if (transactionData.getNodeHash() != null) {
                    addTransactionToUserTs(transactionData, transactionData.getNodeHash());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addTransactionToUserTs(TransactionData transactionData, Hash userHash) {
        UserTrustScoreData userTrustScoreData = userTrustScores.getByHash(userHash);

        if (userTrustScoreData == null) {
            log.error("Transaction can not be added to TS calculation: User {} doesn't exist", userHash);
            return;
        }

        BucketTransactionEventData bucketTransactionEventData
                = (BucketTransactionEventData) buckets.getByHash(getBucketHashByUserHashAndScoreType(userHash, EventType.TRANSACTION));
        if (bucketTransactionEventData == null) {
            log.error("Transaction can not be added to TS calculation: bucket event data doesn't exist for user {}", userHash);
            return;
        }

        if (bucketTransactionEventData.getEventDataHashToEventDataMap().get(transactionData.getHash()) != null) {
            log.debug("Transaction {} is already added to ts calculation", transactionData.getHash());
            return;
        }

        addToTransactionBucketsCalculation(userTrustScoreData, transactionData, bucketTransactionEventData);
    }

    private void createBuckets(UserTrustScoreData usertrustScoreData) {
        try {
            for (EventType score : EventType.values()) {

                BucketData bucketData = (BucketData) score.bucket.getDeclaredConstructor().newInstance();
                bucketData.setUserType(usertrustScoreData.getUserType());
                bucketData.setHash(getBucketHashByUserHashAndScoreType(usertrustScoreData.getHash(), score));

                buckets.put(bucketData);
                usertrustScoreData.getEventTypeToBucketHashMap().put(score, bucketData.getHash());
            }
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            log.error(e.toString());
        }
    }

    private void addToTransactionBucketsCalculation(UserTrustScoreData userTrustScoreData, TransactionData transactionData, BucketTransactionEventData bucketTransactionEventData) {
        TransactionEventData transactionScoreData = new TransactionEventData(transactionData, userTrustScoreData.getUserType());

        if (userTrustScoreData.getUserType() != UserType.FULL_NODE && userTrustScoreData.getUserType() != UserType.DSP_NODE && userTrustScoreData.getUserType() != UserType.TRUST_SCORE_NODE) {
            for (BaseTransactionData baseTransactionData : transactionData.getBaseTransactions()) {
                if (baseTransactionData instanceof ReceiverBaseTransactionData) {
                    AddressUserIndexData addressUserIndexData = addressUserIndex.getByHash(baseTransactionData.getAddressHash());
                    if (addressUserIndexData != null) {
                        UserTrustScoreData receiverUserTrustScoreData;
                        BucketTransactionEventData receiverBucketTransactionEventData = null;
                        if (userTrustScoreData.getHash().equals(addressUserIndexData.getUserHash())) {
                            receiverUserTrustScoreData = userTrustScoreData;
                            receiverBucketTransactionEventData = bucketTransactionEventData;
                        } else {
                            receiverUserTrustScoreData = userTrustScores.getByHash(addressUserIndexData.getUserHash());
                            if (receiverUserTrustScoreData != null) {
                                receiverBucketTransactionEventData = (BucketTransactionEventData) buckets.getByHash(receiverUserTrustScoreData.getEventTypeToBucketHashMap().get(EventType.TRANSACTION));
                            }
                        }
                        if (receiverBucketTransactionEventData != null) {
                            TransactionEventData receiverTransactionScoreData = new TransactionEventData(transactionData, baseTransactionData, receiverUserTrustScoreData.getUserType());
                            bucketTransactionScoreService.addScoreToCalculations(receiverTransactionScoreData, receiverBucketTransactionEventData);
                            buckets.put(receiverBucketTransactionEventData);
                        } else {
                            log.error("Transaction can not be added to balance TS calculation: User {} doesn't exist", addressUserIndexData.getUserHash());
                        }
                    } else {
                        synchronized ((addLockToLockMap(lockUnlinkedAddressesHashMap, baseTransactionData.getAddressHash()))) {
                            UnlinkedAddressData unlinkedAddressData = unlinkedAddresses.getByHash(baseTransactionData.getAddressHash());
                            if (unlinkedAddressData == null) {
                                unlinkedAddressData = new UnlinkedAddressData(baseTransactionData.getAddressHash());
                            }
                            unlinkedAddressData.insertToDateToBalanceMap(transactionScoreData, baseTransactionData.getAmount());
                            unlinkedAddresses.put(unlinkedAddressData);
                            removeLockFromLocksMap(lockUnlinkedAddressesHashMap, baseTransactionData.getAddressHash());
                        }
                    }
                } else if (baseTransactionData instanceof InputBaseTransactionData) {
                    synchronized ((addLockToLockMap(lockUnlinkedAddressesHashMap, baseTransactionData.getAddressHash()))) {
                        UnlinkedAddressData unlinkedAddressData = unlinkedAddresses.getByHash(baseTransactionData.getAddressHash());
                        if (unlinkedAddressData != null) {
                            AddressUserIndexData addressUserIndexData = new AddressUserIndexData(baseTransactionData.getAddressHash(), userTrustScoreData.getHash());
                            addressUserIndex.put(addressUserIndexData);
                            transactionScoreData.SetUnlinkedAddressData(unlinkedAddressData);
                            unlinkedAddresses.delete(unlinkedAddressData);
                            removeLockFromLocksMap(lockUnlinkedAddressesHashMap, baseTransactionData.getAddressHash());
                        }
                    }
                }
            }
        }

        bucketTransactionScoreService.addScoreToCalculations(transactionScoreData, bucketTransactionEventData);
        buckets.put(bucketTransactionEventData);

        if (userTrustScoreData.getUserType() == UserType.MERCHANT && transactionData.getType() == TransactionType.Payment && transactionData.getAmount().doubleValue() > 0) {
            addTransactionToChargeBackBucket(transactionData.getSenderHash(), transactionData);
        }
    }

    private void addTransactionToChargeBackBucket(Hash userHash, TransactionData transactionData) {
        Hash bucketHash = getBucketHashByUserHashAndScoreType(userHash, EventType.CHARGEBACK_SCORE);

        BucketChargeBackFrequencyBasedEventData bucketChargeBackEventsData = (BucketChargeBackFrequencyBasedEventData) buckets.getByHash(bucketHash);
        bucketChargeBackFrequencyBasedScoreService.addPaymentTransactionToCalculations(transactionData, bucketChargeBackEventsData);
        buckets.put(bucketChargeBackEventsData);
    }

    private void updateUserTypeInBuckets(UserTrustScoreData userTrustScoreData) {
        for (Map.Entry<EventType, Hash> eventTypeToBucketHashEntry
                : userTrustScoreData.getEventTypeToBucketHashMap().entrySet()) {
            Hash bucketHash = eventTypeToBucketHashEntry.getValue();
            BucketData bucket = (BucketData) buckets.getByHash(bucketHash);
            if (bucket != null) {
                bucket.setUserType(userTrustScoreData.getUserType());
                buckets.put(bucket);
            }
        }
    }

    private Hash getBucketHashByUserHashAndScoreType(Hash userHash, EventType eventType) {
        return new Hash(ByteBuffer.allocate(userHash.getBytes().length + Integer.BYTES).
                put(userHash.getBytes()).putInt(eventType.getValue()).array());
    }

    private boolean checkIfSignerInList(Hash signerHash) {
        return Arrays.asList(kycServerPublicKeys).contains(signerHash.toString());
    }

    private boolean changingIsLegal(UserTrustScoreData userTrustScoreData) {
        return userTrustScoreData.getUserType().equals(UserType.CONSUMER);
    }

    private Hash addLockToLockMap(Map<Hash, Hash> locksIdentityMap, Hash hash) {
        synchronized (locksIdentityMap) {
            locksIdentityMap.putIfAbsent(hash, hash);
            return locksIdentityMap.get(hash);
        }
    }

    private void removeLockFromLocksMap(Map<Hash, Hash> locksIdentityMap, Hash hash) {
        synchronized (locksIdentityMap) {
            Hash hashLock = locksIdentityMap.get(hash);
            if (hashLock != null) {
                locksIdentityMap.remove(hash);
            }
        }
    }


}