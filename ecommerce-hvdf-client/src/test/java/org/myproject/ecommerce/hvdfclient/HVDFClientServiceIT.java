package org.myproject.ecommerce.hvdfclient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.myproject.ecommerce.core.services.MongoDBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TestApplicationConfiguration.class})
public class HVDFClientServiceIT {
    @Autowired
    private HVDFClientService hvdfClientService;

    @Autowired
    private MongoDBService mongoDBService;

    @Autowired
    private HVDFClientPropertyService hvdfClientPropertyService;

    private List<Long> times = List.of(1516181741620L, 1516182790560L, 1516182882582L,
            1516184589023L, 1516184589524L, 1516535591361L,
            1516535610283L, 1516535706984L, 1516535808443L,
            1516535944773L);

    private static boolean isSetupDone = false;

    @Before
    public void setUp() throws InterruptedException, IOException {
        if(!isSetupDone) {
            LongStream.rangeClosed(times.get(0) / hvdfClientPropertyService.getPeriod(),
                    times.get(times.size() - 1) / hvdfClientPropertyService.getPeriod()).boxed()
                    .map(time -> hvdfClientPropertyService.getChannelPrefix() + String.valueOf(time))
                    .forEach(collection -> mongoDBService.dropCollection("ecommerce", collection));
            times.stream()
                    .forEach(this::setupTestData);
            Thread.sleep(10000);

            isSetupDone = true;
        }
    }

    @After
    public void tearDown() {
    }

    @Test
    public void shouldRecordAListOfAcivities() {
        // given
        List<Activity> activities = new ArrayList<>();
        ActivityDataBuilder builder = new ActivityDataBuilder();
        builder.setUserId("u123").setGeoCode(1).setSessionId("2373BB")
                .setDevice(new Activity.Device("1234", "mobile/iphone", "Chrome/34.0.1847.131"))
                .setType(Activity.Type.VIEW).setItemId("301671").setSku("730223104376")
                .setOrder(new Activity.Order("12520185", 1200))
                .setLocations(Arrays.asList(-86.95444, 33.40178))
                .setTags(Arrays.asList("smartphone", "iphone"));
        LocalDateTime now = LocalDateTime.now();
        builder.setTime(now)
                .setTimeStamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        activities.add(new Activity("u123",
                now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), builder.createActivity()));
        builder = new ActivityDataBuilder();
        builder.setUserId("u457").setGeoCode(1).setSessionId("2373BB")
                .setDevice(new Activity.Device("1234", "mobile/iphone", "Chrome/34.0.1847.131"))
                .setType(Activity.Type.VIEW).setItemId("301671").setSku("730223104376")
                .setOrder(new Activity.Order("12520185", 1200))
                .setLocations(Arrays.asList(-86.95444, 33.40178))
                .setTags(Arrays.asList("smartphone", "iphone"));
        now = LocalDateTime.now();
        builder.setTime(now)
                .setTimeStamp(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        activities.add(new Activity("u457",
                now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), builder.createActivity()));

        // when
        boolean result = hvdfClientService.record(activities, Activity.class);

        // verify
        assertTrue(result);
    }

    @Test
    public void shouldReturnAListofUserActivities() {
        // given
        String userId = "u123";
        long timeStart = 1516181741620L;
        long timeEnd = 1516535944773L;
        int limit = 20;
        Map<String, Object> queryParamMap = Map.of("source", userId, "ts", timeEnd,
                "range", (timeEnd - timeStart), "limit", limit);

        // when
        List<Activity> activities = hvdfClientService.query(queryParamMap);

        // verify
        assertTrue(activities.size() == 10);
        activities.stream()
                .forEach(a -> {
                    Assert.assertEquals(userId, a.getSource());
                    Assert.assertEquals(userId, a.getData().getUserId());
                    Assert.assertTrue(a.getTimeStamp() >= timeStart);
                    Assert.assertTrue(a.getTimeStamp() <= timeEnd);
                });
    }

    private void setupTestData(long time) {
        ActivityDataBuilder builder = new ActivityDataBuilder();
        builder.setUserId("u123").setGeoCode(1).setSessionId("2373BB")
                .setDevice(new Activity.Device("1234", "mobile/iphone", "Chrome/34.0.1847.131"))
                .setType(Activity.Type.VIEW).setItemId("301671").setSku("730223104376")
                .setLocations(Arrays.asList(-86.95444, 33.40178))
                .setTags(Arrays.asList("smartphone", "iphone"));
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.of("UTC"));
        builder.setTime(localDateTime)
                .setTimeStamp(localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        Activity activity = new Activity("u123",
                localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), builder.createActivity());
        if(!hvdfClientService.record(activity)) {
            fail("unable to setup test data");
        }

        builder = new ActivityDataBuilder();
        builder.setUserId("u457").setGeoCode(1).setSessionId("2373BB")
                .setDevice(new Activity.Device("1234", "mobile/iphone", "Chrome/34.0.1847.131"))
                .setType(Activity.Type.ORDER).setItemId("301671").setSku("730223104376")
                .setOrder(new Activity.Order("12520185", 1200))
                .setLocations(Arrays.asList(-86.95444, 33.40178))
                .setTags(Arrays.asList("smartphone", "iphone"));
        localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.of("UTC"));
        builder.setTime(localDateTime)
                .setTimeStamp(localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        activity = new Activity("u457",
                localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), builder.createActivity());
        if(!hvdfClientService.record(activity)) {
            fail("unable to setup test data");
        }
    }
}
