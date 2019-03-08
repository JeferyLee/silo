package de.tum.bgu.msm.models.demography;

import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.container.SiloModelContainer;
import de.tum.bgu.msm.data.person.*;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class EducationModelTest {

    private static EducationModel model;
    private static SiloDataContainer dataContainer;

    @BeforeClass
    public static void setupModel() {
        Properties properties = SiloUtil.siloInitialization(Implementation.MARYLAND, "./test/scenarios/annapolis/javaFiles/siloMstm.properties");
        dataContainer = SiloDataContainer.loadSiloDataContainer(properties);
        SiloModelContainer modelContainer = SiloModelContainer.createSiloModelContainer(dataContainer, null, properties);
        model = modelContainer.getEducationUpdate();
    }

    @Test
    public void testSchoolChange() {
        Person person = PersonUtils.getFactory().createPerson(0, 20, Gender.FEMALE, Race.other, Occupation.EMPLOYED, PersonRole.CHILD, 0, 0);
        person.setSchoolPlace(10);
        ((MstmEducationModelImpl)model).updateEducation(person);
        Assert.assertEquals(0, person.getSchoolPlace());
    }
}
