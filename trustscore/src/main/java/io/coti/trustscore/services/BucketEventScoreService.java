package io.coti.trustscore.services;

import io.coti.trustscore.config.rules.ScoreRules;
import io.coti.trustscore.config.rules.UserScoreRules;
import io.coti.trustscore.data.tsbuckets.BucketBehaviorEventData;
import io.coti.trustscore.data.tsenums.FinalEventType;
import io.coti.trustscore.data.tsenums.EventType;
import io.coti.trustscore.data.tsenums.UserType;
import io.coti.trustscore.data.tsevents.BehaviorEventData;
import io.coti.trustscore.data.contributiondata.EventCountAndContributionData;
import io.coti.trustscore.data.parameters.EventUserParameters;
import io.coti.trustscore.services.calculationservices.BucketEventScoreCalculator;
import io.coti.trustscore.services.interfaces.IBucketService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BucketEventScoreService implements IBucketService<BehaviorEventData, BucketBehaviorEventData> {

    private static Map<UserType, EventUserParameters> FillQuestionnaireUserParametersMap;
    private static Map<UserType, EventUserParameters> FalseQuestionnaireUserParametersMap;
    private static Map<UserType, EventUserParameters> DoubleSpendingUserParametersMap;
    private static Map<UserType, EventUserParameters> InvalidTxUserParametersMap;

    public static EventUserParameters userParameters(FinalEventType finalEventType, UserType userType) {
        switch (finalEventType) {
            case FILLQUESTIONNAIRE:
                return FillQuestionnaireUserParametersMap.get(userType);
            case FALSEQUESTIONNAIRE:
                return FalseQuestionnaireUserParametersMap.get(userType);
            case DOUBLESPENDING:
                return DoubleSpendingUserParametersMap.get(userType);
            case INVALIDTX:
                return InvalidTxUserParametersMap.get(userType);
        }
        return null;
    }

    public static void init(Map<String, ScoreRules> classToScoreRulesMap) {

        FillQuestionnaireUserParametersMap = new ConcurrentHashMap<>();
        FalseQuestionnaireUserParametersMap = new ConcurrentHashMap<>();
        DoubleSpendingUserParametersMap = new ConcurrentHashMap<>();
        InvalidTxUserParametersMap = new ConcurrentHashMap<>();

        for (UserScoreRules userScoreRules : classToScoreRulesMap.get("FillQuestionnaireEventScoreData").getUsers()) {
            FillQuestionnaireUserParametersMap.put(UserType.enumFromString(userScoreRules.getUserType()), new EventUserParameters(userScoreRules));
        }
        for (UserScoreRules userScoreRules : classToScoreRulesMap.get("FalseQuestionnaireEventScoreData").getUsers()) {
            FalseQuestionnaireUserParametersMap.put(UserType.enumFromString(userScoreRules.getUserType()), new EventUserParameters(userScoreRules));
        }
        for (UserScoreRules userScoreRules : classToScoreRulesMap.get("DoubleSpendingEventScoreData").getUsers()) {
            DoubleSpendingUserParametersMap.put(UserType.enumFromString(userScoreRules.getUserType()), new EventUserParameters(userScoreRules));
        }
        for (UserScoreRules userScoreRules : classToScoreRulesMap.get("InvalidTxEventScoreData").getUsers()) {
            InvalidTxUserParametersMap.put(UserType.enumFromString(userScoreRules.getUserType()), new EventUserParameters(userScoreRules));
        }
    }

    @Override
    public BucketBehaviorEventData addScoreToCalculations(BehaviorEventData scoreData, BucketBehaviorEventData bucketData) {
        BucketEventScoreCalculator bucketCalculator = new BucketEventScoreCalculator(bucketData);
        bucketCalculator.decayScores();

        FinalEventType finalEventType = FinalEventType.enumFromString(scoreData.getClass().getSimpleName());
        EventCountAndContributionData eventCountAndContributionData = bucketData.getActualScoresDataMap().get(finalEventType);

        if (eventCountAndContributionData == null) {
            bucketData.getActualScoresDataMap().put(finalEventType, new EventCountAndContributionData(1, 0));
            bucketData.getOldEventsScoreDateMap().put(finalEventType, new ConcurrentHashMap<>());
        } else {
            bucketData.getActualScoresDataMap().put(finalEventType, new EventCountAndContributionData(eventCountAndContributionData.getCountCurrent() + 1, 0));
        }

        bucketCalculator.setCurrentScores();
        return bucketData;
    }

    @Override
    public EventType getScoreType() {
        return EventType.EVENT_SCORE;
    }

    @Override
    public double getBucketSumScore(BucketBehaviorEventData bucketData) {
        BucketEventScoreCalculator bucketCalculator = new BucketEventScoreCalculator(bucketData);
        if (bucketCalculator.decayScores()) {
            bucketCalculator.setCurrentScores();
        }
        return bucketCalculator.getBucketSumScore();
    }
}
