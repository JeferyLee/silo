package sdg;

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.container.DefaultDataContainer;
import de.tum.bgu.msm.data.accessibility.Accessibility;
import de.tum.bgu.msm.data.accessibility.AccessibilityImpl;
import de.tum.bgu.msm.data.accessibility.CommutingTimeProbability;
import de.tum.bgu.msm.data.dwelling.*;
import de.tum.bgu.msm.data.geo.DefaultGeoData;
import de.tum.bgu.msm.data.geo.GeoData;
import de.tum.bgu.msm.data.household.*;
import de.tum.bgu.msm.data.job.*;
import de.tum.bgu.msm.data.person.PersonFactory;
import de.tum.bgu.msm.data.person.PersonFactoryImpl;
import de.tum.bgu.msm.data.person.PersonFactoryMuc;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.io.*;
import de.tum.bgu.msm.io.input.*;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.schools.SchoolData;
import de.tum.bgu.msm.schools.SchoolDataImpl;
import de.tum.bgu.msm.schools.SchoolReader;
import de.tum.bgu.msm.schools.SchoolReaderImpl;
import org.matsim.core.config.Config;
import sdg.data.DataContainerSdg;
import sdg.reader.TripReader;

public class DataBuilderSdg extends DefaultDataContainer {

    public DataBuilderSdg(GeoData geoData, RealEstateDataManager realEstateDataManager, JobDataManager jobDataManager, HouseholdDataManager householdDataManager, TravelTimes travelTimes, Accessibility accessibility, CommutingTimeProbability commutingTimeProbability, Properties properties) {
        super(geoData, realEstateDataManager, jobDataManager, householdDataManager, travelTimes, accessibility, commutingTimeProbability, properties);
    }

    public static DataContainerSdg getModelData(Properties properties, Config config) {

        HouseholdData householdData = new HouseholdDataImpl();
        DwellingData dwellingData = new DwellingDataImpl();
        GeoData geoData = new DefaultGeoData();
        RealEstateDataManager realEstateDataManager = new RealEstateDataManagerImpl(
                DefaultDwellingTypeImpl.values(), dwellingData, householdData, geoData, new DwellingFactoryImpl(), properties);
        SchoolData schoolData = new SchoolDataImpl(geoData, dwellingData, properties);

        final HouseholdFactory hhFactory = new HouseholdFactoryImpl();
        HouseholdDataManager householdDataManager = new HouseholdDataManagerImpl(
                householdData, dwellingData, new PersonFactoryImpl(),
                hhFactory, properties, realEstateDataManager);

        return new DataContainerSdg(geoData, realEstateDataManager, householdDataManager, schoolData, properties);

    }

    static public void read(Properties properties, DataContainerSdg dataContainer, int year){

        GeoDataReader reader = new GeoDataReaderMuc(dataContainer.getGeoData());
        String fileName = properties.main.baseDirectory + properties.geo.zonalDataFile;
        String pathShp = properties.main.baseDirectory + properties.geo.zoneShapeFile;
        reader.readZoneCsv(fileName);
        reader.readZoneShapefile(pathShp);

        String householdFile = properties.main.baseDirectory + properties.householdData.householdFileName;
        householdFile += "_" + year + ".csv";
        HouseholdReader hhReader = new DefaultHouseholdReader(dataContainer.getHouseholdDataManager(), dataContainer.getHouseholdDataManager().getHouseholdFactory());
        hhReader.readData(householdFile);

        String personFile = properties.main.baseDirectory + properties.householdData.personFileName;
        personFile += "_" + year + ".csv";
        PersonReader personReader = new DefaultPersonReader(dataContainer.getHouseholdDataManager());
        personReader.readData(personFile);

        DwellingReader ddReader = new DefaultDwellingReader(dataContainer.getRealEstateDataManager());
        String dwellingsFile = properties.main.baseDirectory + properties.realEstate.dwellingsFileName + "_" + year + ".csv";
        ddReader.readData(dwellingsFile);

//        new JobType(properties.jobData.jobTypes);
//        JobReader jjReader = new DefaultJobReader(dataContainer.getJobDataManager());
//        String jobsFile = properties.main.baseDirectory + properties.jobData.jobsFileName + "_" + year + ".csv";
//        jjReader.readData(jobsFile);

        SchoolReader ssReader = new SchoolReaderImpl(dataContainer.getSchoolData());
        String schoolsFile = properties.main.baseDirectory + properties.schoolData.schoolsFileName + "_" + year + ".csv";
        ssReader.readData(schoolsFile);

        TripReader tripReader = new TripReader();
        String tripFileName = "F:\\models\\muc\\scenOutput\\test\\2011\\microData\\trips.csv";
        tripReader.readData(tripFileName, dataContainer);
    }
}