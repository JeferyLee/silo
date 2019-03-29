package de.tum.bgu.msm.data.dwelling;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.pb.common.datafile.TableDataSet;
import de.tum.bgu.msm.data.Id;
import de.tum.bgu.msm.data.SummarizeData;
import de.tum.bgu.msm.data.development.Development;
import de.tum.bgu.msm.data.development.DevelopmentImpl;
import de.tum.bgu.msm.data.geo.GeoData;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.household.HouseholdData;
import de.tum.bgu.msm.data.household.HouseholdUtil;
import de.tum.bgu.msm.data.household.IncomeCategory;
import de.tum.bgu.msm.io.output.DefaultDwellingWriter;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;

import java.util.*;

import static de.tum.bgu.msm.data.dwelling.RealEstateUtils.RENT_CATEGORIES;

/**
 * Keeps data of dwellings and non-residential floorspace
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 7 January 2010 in Rhede
 **/
public class RealEstateDataManagerImpl implements RealEstateDataManager {

    private final static Logger logger = Logger.getLogger(RealEstateDataManagerImpl.class);

    private final DwellingData dwellingData;

    private final HouseholdData householdData;
    private final GeoData geoData;

    private final DwellingFactory dwellingFactory;
    private final Properties properties;

    public static int largestNoBedrooms;

    private int[] dwellingsByQuality;
    private double[] initialQualityShares;
    private int highestDwellingIdInUse;
    private static final Map<IncomeCategory, Map<Integer, Float>> ddPriceByIncomeCategory = new EnumMap<>(IncomeCategory.class);

    private Map<Integer, Set<Dwelling>> vacDwellingsByRegion = new LinkedHashMap<>();

    private double[] avePrice;
    private double[] aveVac;

    private final List<DwellingType> dwellingTypes = new ArrayList<>();

    public RealEstateDataManagerImpl(List<DwellingType> dwellingTypes, DwellingData dwellingData,
                                     HouseholdData householdData, GeoData geoData,
                                     DwellingFactory dwellingFactory, Properties properties) {
        this.dwellingData = dwellingData;
        this.householdData = householdData;
        this.geoData = geoData;
        this.dwellingFactory = dwellingFactory;
        this.properties = properties;
        this.dwellingTypes.addAll(dwellingTypes);
    }

    @Override
    public DwellingFactory getDwellingFactory() {
        return dwellingFactory;
    }

    @Override
    public void setup() {
        readDevelopmentData();
        fillQualityDistribution();
        setHighestVariablesAndCalculateRentShareByIncome();
        identifyVacantDwellings();
    }

    @Override
    public void prepareYear(int year) {
        calculateRegionWidePriceAndVacancyByDwellingType();
    }

    @Override
    public void endYear(int year) {}

    @Override
    public void endSimulation() {
        String filedd = Properties.get().main.baseDirectory
                + properties.realEstate.dwellingsFinalFileName
                + "_"
                + properties.main.endYear
                + ".csv";
        new DefaultDwellingWriter(this).writeDwellings(filedd);
    }

    @Override
    public Map<Integer, Float> getRentPaymentsForIncomeGroup(IncomeCategory incomeCategory) {
        return ddPriceByIncomeCategory.get(incomeCategory);
    }

    @Override
    public int getNextDwellingId() {
        // increase highestDwellingIdInUse by 1 and return value
        return ++highestDwellingIdInUse;
    }

    @Override
    public double[] getInitialQualShares() {
        return initialQualityShares;
    }

    @Override
    public double[] getCurrentQualShares() {
        double[] currentQualityShares = new double[Properties.get().main.qualityLevels];
        for (int qual = 1; qual <= Properties.get().main.qualityLevels; qual++)
            currentQualityShares[qual - 1] =
                    (double) dwellingsByQuality[qual - 1] / (double) SiloUtil.getSum(dwellingsByQuality);
        return currentQualityShares;
    }

    /**
     * Return array with IDs of vacant dwellings in region
     * @param region
     * @return
     */
    @Override
    public int[] getListOfVacantDwellingsInRegion(int region) {
        return vacDwellingsByRegion.getOrDefault(region,
                new HashSet<>()).stream().mapToInt(Id::getId).toArray();
    }

    @Override
    public int getNumberOfVacantDDinRegion(int region) {
        return vacDwellingsByRegion.getOrDefault(region, new HashSet<>()).size();
    }

    @Override
    public List<DwellingType> getDwellingTypes() {
        return Collections.unmodifiableList(dwellingTypes);
    }

    @Override
    public Dwelling getDwelling(int dwellingId) {
        return dwellingData.getDwelling(dwellingId);
    }

    @Override
    public Collection<Dwelling> getDwellings() {
        return Collections.unmodifiableCollection(dwellingData.getDwellings());
    }

    @Override
    public void removeDwelling(int id) {
        dwellingData.removeDwelling(id);
    }

    @Override
    public void addDwelling(Dwelling dwelling) {
        this.dwellingData.addDwelling(dwelling);
    }

    @Override
    public int[] getDwellingsByQuality() {
        return dwellingsByQuality;
    }

    /**
     * Walk through all dwellings and identify vacant dwellings
     * (one-time task at beginning of model run only)
     */
    private void identifyVacantDwellings() {
        logger.info("  Identifying vacant dwellings");
        for (Dwelling dd : dwellingData.getDwellings()) {
            if (dd.getResidentId() == -1) {
                int dwellingId = dd.getId();
                int region = geoData.getZones().get(dd.getZoneId()).getRegion().getId();
                vacDwellingsByRegion.putIfAbsent(region, new LinkedHashSet<>());
                vacDwellingsByRegion.get(region).add(dd);
                if (dwellingId == SiloUtil.trackDd)
                    SiloUtil.trackWriter.println("Added dwelling " + dwellingId + " to list of vacant dwelling.");
            }
        }
    }

    /**
     *  Count number of dwellings by quality and calculate average quality
     */
    private void fillQualityDistribution() {
        int numberOfQualityLevels = properties.main.qualityLevels;
        dwellingsByQuality = new int[numberOfQualityLevels];
        initialQualityShares = new double[numberOfQualityLevels];
        for (Dwelling dd : getDwellings()) {
            dwellingsByQuality[dd.getQuality() - 1]++;
        }
        for (int qual = 1; qual <= numberOfQualityLevels; qual++) {
            initialQualityShares[qual - 1] = (double) dwellingsByQuality[qual - 1] /
                    (double) SiloUtil.getSum(dwellingsByQuality);
        }
    }


    private void setHighestVariablesAndCalculateRentShareByIncome() {
        // identify highest dwelling ID in use and largest bedrooms, also calculate share of rent paid by each hh type
        // only done initially when model starts

        highestDwellingIdInUse = 0;
        largestNoBedrooms = 0;

        // identify how much rent (specified by 25 rent categories) is paid by households of each income category
        Map<IncomeCategory, Multiset<Integer>> countOfHouseholdsByIncomeAndRentCategory = new EnumMap<>(IncomeCategory.class);
        for (IncomeCategory incomeCat : IncomeCategory.values()) {
            countOfHouseholdsByIncomeAndRentCategory.put(incomeCat, HashMultiset.create());
        }
        for (Dwelling dd : dwellingData.getDwellings()) {
            highestDwellingIdInUse = Math.max(highestDwellingIdInUse, dd.getId());
            largestNoBedrooms = Math.max(largestNoBedrooms, dd.getBedrooms());
            int hhId = dd.getResidentId();
            if (hhId > 0) {
                int hhinc = HouseholdUtil.getHhIncome(householdData.getHousehold(hhId));
                IncomeCategory incomeCategory = HouseholdUtil.getIncomeCategoryForIncome(hhinc);
                int rentCategory = (int) ((dd.getPrice() * 1.) / 200.);  // rent category defined as <rent/200>
                rentCategory = Math.min(rentCategory, RENT_CATEGORIES);   // ensure that rent categories do not exceed max
                countOfHouseholdsByIncomeAndRentCategory.get(incomeCategory).add(rentCategory);
            }
        }
        IncomeCategory highestIncCat = IncomeCategory.values()[IncomeCategory.values().length - 1];
        countOfHouseholdsByIncomeAndRentCategory.get(highestIncCat).add(RENT_CATEGORIES);  // make sure that most expensive category can be afforded by richest households
        for (IncomeCategory incomeCategory : IncomeCategory.values()) {
            float sum = countOfHouseholdsByIncomeAndRentCategory.get(incomeCategory).size();
            Map<Integer, Float> shareOfRentsForThisIncCat = new HashMap<>();
            for (int rentCategory = 0; rentCategory <= RENT_CATEGORIES; rentCategory++) {
                int thisRentAndIncomeCat = countOfHouseholdsByIncomeAndRentCategory.get(incomeCategory).count(rentCategory);
                if (sum != 0) {
                    shareOfRentsForThisIncCat.put(rentCategory, thisRentAndIncomeCat / sum);
                } else {
                    //todo if there is not a househould of this rent and this category the shares should be zero?
                    shareOfRentsForThisIncCat.put(rentCategory, 0.f);
                }
            }
            ddPriceByIncomeCategory.put(incomeCategory, shareOfRentsForThisIncCat);
        }
    }


    public void summarizeDwellings() {
        // aggregate dwellings

        logger.info("****** Average Price is Written *******");

        SummarizeData.resultFile("QualityLevel,Dwellings");
        for (int qual = 1; qual <= Properties.get().main.qualityLevels; qual++) {
            String row = qual + "," + dwellingsByQuality[qual - 1];
            SummarizeData.resultFile(row);
        }

        Multiset<DwellingType> countsByDwellingType = HashMultiset.create(dwellingTypes.size());

        for (Dwelling dd : dwellingData.getDwellings()) {
            countsByDwellingType.add(dd.getType());
        }
        for (DwellingType dt : dwellingTypes) {
            SummarizeData.resultFile("CountOfDD," + dt.toString() + "," + countsByDwellingType.count(dt));
        }
        for (DwellingType dt : dwellingTypes) {
            double avePrice = getAveragePriceByDwellingType()[dwellingTypes.indexOf(dt)];
            SummarizeData.resultFile("AveMonthlyPrice," + dt.toString() + "," + avePrice);
        }
        for (DwellingType dt : dwellingTypes) {
            double aveVac = getAverageVacancyByDwellingType()[dwellingTypes.indexOf(dt)];
            Formatter f = new Formatter();
            f.format("AveVacancy,%s,%f", dt.toString(), aveVac);
            SummarizeData.resultFile(f.toString());
        }
        // aggregate developable land
        SummarizeData.resultFile("Available land for construction by region");
        final int highestId = geoData.getRegions().keySet()
                .stream().mapToInt(Integer::intValue).max().getAsInt();
        double[] availLand = new double[highestId + 1];
        for (int zone : geoData.getZones().keySet()) {
            availLand[geoData.getZones().get(zone).getRegion().getId()] +=
                    getAvailableCapacityForConstruction(zone);
        }
        for (int region : geoData.getRegions().keySet()) {
            Formatter f = new Formatter();
            f.format("%d,%f", region, availLand[region]);
            SummarizeData.resultFile(f.toString());
        }

        // summarize housing costs by income group
        SummarizeData.resultFile("Housing costs by income group");
        String header = "Income";
        for (int i = 0; i < 10; i++) header = header.concat(",rent_" + ((i + 1) * 250));
        header = header.concat(",averageRent");
        SummarizeData.resultFile(header);
        int[][] rentByIncome = new int[10][10];
        int[] rents = new int[10];
        for (Household hh : householdData.getHouseholds()) {
            int hhInc = HouseholdUtil.getHhIncome(hh);
            int rent = dwellingData.getDwelling(hh.getDwellingId()).getPrice();
            int incCat = Math.min((hhInc / 10000), 9);
            int rentCat = Math.min((rent / 250), 9);
            rentByIncome[incCat][rentCat]++;
            rents[incCat] += rent;
        }
        for (int i = 0; i < 10; i++) {
            String line = String.valueOf((i + 1) * 10000);
            int countThisIncome = 0;
            for (int r = 0; r < 10; r++) {
                line = line.concat("," + rentByIncome[i][r]);
                countThisIncome += rentByIncome[i][r];
            }
            if (countThisIncome != 0) { // new dz, avoid dividing by zero
                // TODO check what happens by leaving this out... the error is avoided
                line = line.concat("," + rents[i] / countThisIncome);
            }
            SummarizeData.resultFile(line);
        }
    }


    /**
     *  Remove dwelling with ID ddId from list of vacant dwellings
     * @param ddId
     */
    @Override
    public void removeDwellingFromVacancyList(int ddId) {

        boolean found = false;

        Dwelling dwelling = dwellingData.getDwelling(ddId);
        int region = geoData.getZones().get(dwelling.getZoneId()).getRegion().getId();
        Set<Dwelling> vacDwellings = vacDwellingsByRegion.get(region);
        if (vacDwellings != null) {
            found = vacDwellings.remove(dwelling);
            if (ddId == SiloUtil.trackDd) {
                SiloUtil.trackWriter.println("Removed dwelling " + ddId +
                        " from list of vacant dwellings.");
            }
        }

        if (!found) {
            logger.warn("Consistency error: Could not find vacant dwelling "
                    + ddId + " in vacDwellingsByRegion.");
        }
    }

    /**
     * Add dwelling to vacancy list
     * @param dd
     */
    @Override
    public void addDwellingToVacancyList(Dwelling dd) {

        int region = geoData.getZones().get(dd.getZoneId()).getRegion().getId();
        vacDwellingsByRegion.putIfAbsent(region, new LinkedHashSet<>());
        vacDwellingsByRegion.get(region).add(dd);
        if (dd.getId() == SiloUtil.trackDd) {
            SiloUtil.trackWriter.println("Added dwelling " + dd.getId() +
                    " to list of vacant dwellings.");
        }
    }


    private void calculateRegionWidePriceAndVacancyByDwellingType() {
        // calculate region-wide average dwelling costs and vacancy by dwelling type

        int distinctDdTypes = dwellingTypes.size();
        int[][] vacOcc = SiloUtil.setArrayToValue(new int[2][distinctDdTypes], 0);
        long[] price = SiloUtil.setArrayToValue(new long[distinctDdTypes], 0);

        for (Dwelling dd : dwellingData.getDwellings()) {
            int dto = dwellingTypes.indexOf(dd.getType());
            price[dto] += dd.getPrice();

            if (dd.getResidentId() > 0) {
                vacOcc[1][dto]++;
            } else {
                vacOcc[0][dto]++;
            }
        }
        aveVac = new double[distinctDdTypes];
        avePrice = new double[distinctDdTypes];

        for (DwellingType dt : dwellingTypes) {
            int dto = dwellingTypes.indexOf(dt);

            if (vacOcc[0][dto] + vacOcc[1][dto] > 0) {
                aveVac[dto] = (double) vacOcc[0][dto] / (double) (vacOcc[0][dto] + vacOcc[1][dto]);
                avePrice[dto] = price[dto] / (double) (vacOcc[0][dto] + vacOcc[1][dto]);

            } else {
                aveVac[dto] = 0;
                avePrice[dto] = 0;
            }
        }
    }


    @Override
    public double[][] getVacancyRateByTypeAndRegion() {
        // calculate vacancy rate by region and dwelling type
        final int highestRegionId = geoData.getRegions().keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
        int[][][] vacOcc = SiloUtil.setArrayToValue(new int[2][dwellingTypes.size()][highestRegionId + 1], 0);

        for (Dwelling dd : dwellingData.getDwellings()) {
            int dto = dwellingTypes.indexOf(dd.getType());
            if (dd.getResidentId() > 0) {
                vacOcc[1][dto][geoData.getZones().get(dd.getZoneId()).getRegion().getId()]++;
            } else {
                vacOcc[0][dto][geoData.getZones().get(dd.getZoneId()).getRegion().getId()]++;
            }
        }

        double[][] vacRate = new double[dwellingTypes.size()][highestRegionId + 1];
        for (DwellingType dt : dwellingTypes) {
            int dto = dwellingTypes.indexOf(dt);
            for (int region : geoData.getRegions().keySet()) {
                if ((vacOcc[0][dto][region] + vacOcc[1][dto][region]) > 0) {
                    vacRate[dto][region] = (double) vacOcc[0][dto][region] / (double) (vacOcc[0][dto][region] + vacOcc[1][dto][region]);
                } else {
                    vacRate[dto][region] = 0.;
                }
            }
        }
        return vacRate;
    }


    @Override
    public void setAvePriceByDwellingType(double[] newAvePrice) {
        avePrice = newAvePrice;
    }


    @Override
    public double[] getAveragePriceByDwellingType() {
        return avePrice;
    }


    @Override
    public double[] getAverageVacancyByDwellingType() {
        return aveVac;
    }


    @Override
    public int[][] getDwellingCountByTypeAndRegion() {
        // return number of dwellings by type and region
        final int highestRegionId = geoData.getRegions().keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
        int[][] dwellingCount =
                SiloUtil.setArrayToValue(new int[dwellingTypes.size()][highestRegionId + 1], 1);

        for (Dwelling dd : dwellingData.getDwellings()) {
            dwellingCount[dwellingTypes.indexOf(dd.getType())][geoData.getZones().get(dd.getZoneId()).getRegion().getId()]++;
        }
        return dwellingCount;
    }


    @Override
    public double getAvailableCapacityForConstruction(int zone) {
        // return available land in developable land-use categories
        double sm;
        Development development = geoData.getZones().get(zone).getDevelopment();
        if (development.isUseDwellingCapacity()) {
            sm = development.getDwellingCapacity();
        } else {
            sm = development.getDevelopableArea();
        }
        return sm;
    }

    /**
     * Remove acres from developable land
     * @param zone
     * @param acres
     */
    @Override
    public void convertLand(int zone, float acres) {
        Development development = geoData.getZones().get(zone).getDevelopment();
        if (development.isUseDwellingCapacity()) {
            development.changeCapacityBy(-1);
        } else {
            development.changeAreaBy(-acres);
        }
    }

    private void readDevelopmentData() {
        String baseDirectory = Properties.get().main.baseDirectory;

        TableDataSet developmentTable = SiloUtil.readCSVfile(baseDirectory + Properties.get().geo.landUseAndDevelopmentFile);

        int[] zoneIdData = developmentTable.getColumnAsInt("Zone");
        Map<DwellingType, int[]> constraintData = new HashMap<>();
        for (DwellingType dwellingType : dwellingTypes) {
            constraintData.put(dwellingType, developmentTable.getColumnAsInt(dwellingType.toString()));
        }
        int[] dwellingCapacityData = developmentTable.getColumnAsInt("DevCapacity");
        double[] landUseData = developmentTable.getColumnAsDouble("DevLandUse");

        for (int i = 0; i < zoneIdData.length; i++) {

            Map<DwellingType, Boolean> constraints = new HashMap<>();
            for (DwellingType dwellingType : dwellingTypes) {
                constraints.put(dwellingType, constraintData.get(dwellingType)[i] == 1);
            }

            Development development = new DevelopmentImpl(landUseData[i], dwellingCapacityData[i], constraints, Properties.get().geo.useCapacityForDwellings);
            geoData.getZones().get(zoneIdData[i]).setDevelopment(development);
        }

    }

    /**
     * Vacates a dwelling by setting resident id to -1 and adding the dwelling to the vacancy list.
     *
     * @param idOldDD
     */
    @Override
    public void vacateDwelling(int idOldDD) {
        Dwelling dd = getDwelling(idOldDD);
        dd.setResidentID(-1);
        addDwellingToVacancyList(dd);
    }
}