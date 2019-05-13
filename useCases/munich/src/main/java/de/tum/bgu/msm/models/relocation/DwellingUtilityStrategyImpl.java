package de.tum.bgu.msm.models.relocation;

import de.tum.bgu.msm.data.household.HouseholdType;
import de.tum.bgu.msm.util.js.JavaScriptCalculator;

import java.io.InputStreamReader;
import java.io.Reader;

public class DwellingUtilityStrategyImpl extends JavaScriptCalculator<Double> implements DwellingUtilityStrategy {

    private final static Reader reader
            = new InputStreamReader(DwellingUtilityStrategyImpl.class.getResourceAsStream("DwellingUtilityCalc"));


    public DwellingUtilityStrategyImpl() {
        super(reader);
    }

    public double calculateSelectDwellingUtility(HouseholdType ht, double ddSizeUtility, double ddPriceUtility,
                                                 double ddQualityUtility, double ddAutoAccessibilityUtility,
                                                 double transitAccessibilityUtility, double ddWorkDistanceUtility) {
       return super.calculate("calculateSelectDwellingUtility", ht, ddSizeUtility, ddPriceUtility,
                ddQualityUtility, ddAutoAccessibilityUtility,
                transitAccessibilityUtility, ddWorkDistanceUtility);
    }
}