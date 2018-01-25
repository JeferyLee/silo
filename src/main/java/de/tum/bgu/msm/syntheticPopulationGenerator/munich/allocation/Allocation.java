package de.tum.bgu.msm.syntheticPopulationGenerator.munich.allocation;


import de.tum.bgu.msm.data.Job;
import de.tum.bgu.msm.data.SummarizeData;
import de.tum.bgu.msm.properties.PropertiesSynPop;
import de.tum.bgu.msm.syntheticPopulationGenerator.DataSetSynPop;
import de.tum.bgu.msm.syntheticPopulationGenerator.ModuleSynPop;
import org.apache.log4j.Logger;

import java.util.Collections;

public class Allocation extends ModuleSynPop{

    private static final Logger logger = Logger.getLogger(Allocation.class);

    public Allocation(DataSetSynPop dataSetSynPop){
        super(dataSetSynPop);
    }

    @Override
    public void run(){
        logger.info("   Started allocation model.");
        if (PropertiesSynPop.get().main.runAllocation) {
            generateHouseholdsPersonsDwellings();
            generateJobs();
        } else {
            readPopulation();
        }
        assignJobs();
        assignSchools();
        validateTripLengths();
        logger.info("   Completed allocation model.");

    }

    public void generateHouseholdsPersonsDwellings(){
        GenerateHouseholdsPersonsDwellings generate = new GenerateHouseholdsPersonsDwellings(dataSetSynPop);
        generate.run();
        SummarizeData.writeOutSyntheticPopulationDE(1990);
    }

    public void generateJobs(){
        GenerateJobs generate = new GenerateJobs(dataSetSynPop);
        generate.run();
        SummarizeData.writeOutSyntheticPopulationDE(1991);
    }

    public void assignJobs(){
        AssignJobs assignJobs = new AssignJobs(dataSetSynPop);
        assignJobs.run();
    }

    public void assignSchools(){
        AssignSchools assignSchools = new AssignSchools(dataSetSynPop);
        assignSchools.run();
        SummarizeData.writeOutSyntheticPopulationDE(1992);
    }

    public void readPopulation(){
        ReadPopulation readPopulation = new ReadPopulation();
        readPopulation.run();
    }

    public void validateTripLengths(){
        ValidateTripLengthDistribution validateTripLengthDistribution = new ValidateTripLengthDistribution(dataSetSynPop);
        validateTripLengthDistribution.run();
    }

}
